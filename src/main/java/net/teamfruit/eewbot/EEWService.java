package net.teamfruit.eewbot;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EEWService {

    private final GatewayDiscordClient gateway;
    private final Map<Long, Channel> channels;
    private final ReentrantReadWriteLock lock;
    private final Optional<TextChannel> systemChannel;
    private final MinimalHttpAsyncClient httpClient;

    public EEWService(final GatewayDiscordClient gateway, final Map<Long, Channel> map, final ReentrantReadWriteLock lock, final Optional<TextChannel> systemChannel, int poolingMax, int poolingMaxPerRoute) {
        this.gateway = gateway;
        this.channels = map;
        this.lock = lock;
        this.systemChannel = systemChannel;
        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(poolingMax)
                .setMaxConnPerRoute(poolingMaxPerRoute)
                .build();
        this.httpClient = HttpAsyncClients.createMinimal(
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.DEFAULT,
                connectionManager);
        this.httpClient.start();
    }

    public void sendMessage(final String key, final Entity entity) {
        sendMessage(channel -> channel.value(key), entity);
    }

    public void sendMessage(final Predicate<Channel> filter, final Entity entity) {
        Map<String, MessageCreateSpec> cacheMsg = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> cacheMsg.put(lang, entity.createMessage(lang)));

        HttpHost target = new HttpHost("https", "discord.com");
        Map<String, SimpleHttpRequest> cacheWebhook = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> cacheWebhook.put(lang, SimpleRequestBuilder.post()
                .setHttpHost(target)
                .setBody(entity.createWebhook(lang).json(), ContentType.APPLICATION_JSON)
                .build()));

        Map<Boolean, List<Map.Entry<Long, Channel>>> partitioned = this.channels.entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().webhook != null));

        this.lock.readLock().lock();
        try {
            final Future<AsyncClientEndpoint> leaseFuture = this.httpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(partitioned.get(true).size());
                partitioned.get(true).forEach(entry -> {
                    SimpleHttpRequest request = cacheWebhook.get(entry.getValue().lang);
                    request.setPath("/api/webhooks" + entry.getValue().webhook.getJoined());
                    endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse simpleHttpResponse) {
                            latch.countDown();
                        }

                        @Override
                        public void failed(Exception e) {
                            latch.countDown();
                            Log.logger.info("Failed to send message: ChannelID={} Message={}", entry.getKey(), e.getMessage());
                            MessageCreateSpec spec = cacheMsg.get(entry.getValue().lang);
                            directSendMessage(entry.getKey(), spec).subscribe();
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            Log.logger.info("Failed to send message: ChannelID={}", entry.getKey());
                            MessageCreateSpec spec = cacheMsg.get(entry.getValue().lang);
                            directSendMessage(entry.getKey(), spec).subscribe();
                        }
                    });
                });
                latch.await();
            } finally {
                endpoint.releaseAndReuse();
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.logger.error("Failed to send message");
        }

        partitioned.get(false).forEach(entry -> {
            MessageCreateSpec spec = cacheMsg.get(entry.getValue().lang);
            directSendMessage(entry.getKey(), spec).subscribe();
        });

//        Flux.merge(this.channels.entrySet().stream()
//                        .filter(entry -> entry.getValue().webhook == null)
//                        .filter(entry -> filter.test(entry.getValue()))
//                        .map(entry -> directSendMessage(entry.getKey(), cache.get(entry.getValue().lang)))
//                        .collect(Collectors.toList()))
//                .parallel()
//                .runOn(Schedulers.parallel())
//                .groups()
//                .subscribe(Flux::subscribe);

        this.lock.readLock().unlock();
    }

    public Mono<Message> sendMessagePassErrors(long channelId, final MessageCreateSpec spec) {
        return directSendMessagePassErrors(channelId, spec);
    }

//    public void sendAttachment(final String key, final Function<String, MessageCreateSpec> spec) {
//        sendAttachment(channel -> channel.value(key), spec);
//    }

//    public void sendAttachment(final Predicate<Channel> filter, final Function<String, MessageCreateSpec> spec) {
//        if (this.systemChannel.isPresent())
//            directSendMessage(this.systemChannel.get().getId().asLong(), spec.apply(null))
//                    .map(msg -> msg.getAttachments().iterator().next().getUrl())
//                    .subscribe(url -> sendMessage(filter, lang -> MessageCreateSpec.builder().content(url).build()));
//        else
//            sendMessage(filter, spec);
//    }

    private Mono<Message> directSendMessage(final long channelId, final MessageCreateSpec spec) {
        return directSendMessagePassErrors(channelId, spec)
                .doOnError(ClientException.class, err -> {
                    Log.logger.error("Failed to send message: ChannelID={} Message={}", channelId, err.getMessage());
                    if (err.getStatus() == HttpResponseStatus.NOT_FOUND || err.getStatus() == HttpResponseStatus.FORBIDDEN) {
                        this.lock.writeLock().lock();
                        if (this.channels.remove(channelId) != null)
                            Log.logger.info(err.getStatus() == HttpResponseStatus.NOT_FOUND ? "Channel {} has been deleted, unregister" : "Missing permissions {}", channelId);
                        this.lock.writeLock().unlock();
                    }
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }
}
