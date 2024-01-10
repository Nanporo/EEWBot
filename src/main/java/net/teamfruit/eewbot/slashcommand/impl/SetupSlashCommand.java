package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.WebhookCreateRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Webhook;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SetupSlashCommand implements ISelectMenuSlashCommand {
    @Override
    public String getCommandName() {
        return "setup";
    }

    @Override
    public boolean isDefer() {
        return true;
    }

    @Override
    public boolean isEphemeral() {
        return true;
    }

    @Override
    public List<String> getCustomIds() {
        return Arrays.asList("channel", "sensitivity");
    }

    @Override
    public boolean isDeferOnSelect() {
        return true;
    }

    @Override
    public boolean isEphemeralOnSelect() {
        return true;
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("現在のチャンネルにBotが通知する情報を設定します。")
                .defaultPermission(false)
                .defaultMemberPermissions(String.valueOf(Permission.MANAGE_GUILD.getValue()))
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        return Mono.fromRunnable(() -> bot.getChannels().computeIfAbsent(channelId, key -> new Channel(false, false, false, false, SeismicIntensity.ONE, null)))
                .then(Mono.justOrEmpty(event.getInteraction().getGuildId())
                        .flatMap(guildId -> event.getInteraction().getChannel()
                                .filter(GuildChannel.class::isInstance)
                                .cast(GuildChannel.class)
                                .flatMap(guildChannel -> guildChannel.getEffectivePermissions(event.getClient().getSelfId())
                                        .filter(perms -> perms.contains(Permission.MANAGE_WEBHOOKS))
                                        .flatMap(perms -> event.getInteraction().getGuild()
                                                .flatMap(guild -> guild.getWebhooks()
                                                        .filter(webhook -> {
                                                            long targetChannelId = guildChannel instanceof ThreadChannel
                                                                    ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElse(-1L)
                                                                    : channelId;
                                                            boolean isSameChannel = webhook.getChannelId().asLong() == targetChannelId;
                                                            boolean isCreatedBySelf = webhook.getCreator()
                                                                    .filter(user -> user.getId().equals(event.getClient().getSelfId()))
                                                                    .isPresent();
                                                            return isSameChannel && isCreatedBySelf;
                                                        })
                                                        .next()
                                                        .map(discord4j.core.object.entity.Webhook::getData)
                                                        .switchIfEmpty(
                                                                guild.getSelfMember().map(PartialMember::getDisplayName)
                                                                        .flatMap(name -> event.getClient().getRestClient().getWebhookService()
                                                                                .createWebhook(channelId, WebhookCreateRequest.builder()
                                                                                        .name(name)
                                                                                        .build(), "Create EEWBot webhook")))
                                                        .flatMap(webhookData -> Mono.fromRunnable(() -> {
                                                            Webhook webhook = new Webhook(webhookData.id().asLong(), webhookData.token().get(), guildChannel instanceof ThreadChannel ? channelId : null);
                                                            bot.getChannels().setWebhook(channelId, webhook);
                                                            try {
                                                                bot.getChannels().save();
                                                            } catch (IOException e) {
                                                                Log.logger.error("Failed to save channel registry during setup command", e);
                                                                throw new RuntimeException(e);
                                                            }
                                                        })).then(buildReply(bot, event, lang, channelId, false))
                                                ))
                                        .switchIfEmpty(buildReply(bot, event, lang, channelId, true))) // No webhook perm
                        ).switchIfEmpty(buildReply(bot, event, lang, channelId, false))) //DM
                .then();
    }

    private Mono<Message> buildReply(EEWBot bot, ApplicationCommandInteractionEvent event, String lang, long channelId, boolean noWebhook) {
        return event.getInteraction().getChannel()
                .flatMap(channel -> Mono.just(channel)
                        .filter(GuildChannel.class::isInstance)
                        .cast(GuildChannel.class)
                        .flatMap(guildChannel -> guildChannel.getEffectivePermissions(bot.getClient().getSelfId()))
                        .filter(perms -> !perms.contains(Permission.VIEW_CHANNEL) || !perms.contains(Permission.SEND_MESSAGES))
                        .flatMap(perms -> {
                            if (!perms.contains(Permission.VIEW_CHANNEL))
                                return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.viewchannel")).withEphemeral(true);
                            return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.sendmessages")).withEphemeral(true);
                        }))
                .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.viewchannel"))
                        .withEphemeral(true))
                .switchIfEmpty(event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.reply") + (noWebhook ? "\n\n" + I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.managewebhooks") : ""))
                        .withComponents(ActionRow.of(buildMainSelectMenu(bot, channelId, lang)), ActionRow.of(buildSensitivitySelectMenu(bot, channelId, lang)))
                );
    }

    private SelectMenu buildMainSelectMenu(EEWBot bot, long channelId, String lang) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getCommandFields();
        return SelectMenu.of("channel", fields.entrySet().stream().map(entry -> {
                    String label = I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel." + entry.getKey().toLowerCase() + ".label");
                    SelectMenu.Option option = entry.getValue() ? SelectMenu.Option.ofDefault(label, entry.getKey()) : SelectMenu.Option.of(label, entry.getKey());
                    option = option.withDescription(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel." + entry.getKey().toLowerCase() + ".desc"));
                    return option;
                }).collect(Collectors.toList()))
                .withPlaceholder(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel.placeholder"))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(EEWBot bot, long channelId, String lang) {
        Channel channel = bot.getChannels().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    String label = intensity == SeismicIntensity.UNKNOWN ? I18n.INSTANCE.get(lang, "eewbot.scmd.setup.sensitivity.option.unknown") : I18n.INSTANCE.format(lang, "eewbot.scmd.setup.sensitivity.option", intensity);
                    if (channel.getMinIntensity() == intensity)
                        return SelectMenu.Option.ofDefault(label, intensity.getSimple());
                    return SelectMenu.Option.of(label, intensity.getSimple());
                }).collect(Collectors.toList()))
                .withPlaceholder(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.sensitivity.placeholder"))
                .withMinValues(1)
                .withMaxValues(1);
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        if (event.getCustomId().equals("channel"))
            return applyChannel(bot, event, lang).then();
        else if (event.getCustomId().equals("sensitivity"))
            return applySensitivity(bot, event, lang).then();
        return Mono.empty();
    }

    private Mono<Message> applyChannel(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        Channel.COMMAND_KEYS.forEach(name -> bot.getChannels().set(channelId, name, event.getValues().contains(name)));
        try {
            bot.getChannels().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        if (event.getValues().isEmpty())
            return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel.followup.none"));
        return event.createFollowup(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.channel.followup.any",
                event.getValues().stream()
                        .map(value -> Channel.toCommandName(value).orElse("")).collect(Collectors.joining(", "))));
    }

    private Mono<Message> applySensitivity(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        SeismicIntensity intensity = SeismicIntensity.get(event.getValues().get(0));
        bot.getChannels().setMinIntensity(channelId, intensity);
        try {
            bot.getChannels().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(I18n.INSTANCE.format(lang, intensity != SeismicIntensity.UNKNOWN ? "eewbot.scmd.setup.sensitivity.followup" : "eewbot.scmd.setup.sensitivity.followup.unknown", intensity.getSimple()));
    }
}
