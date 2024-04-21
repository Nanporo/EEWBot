package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE52 extends JMAReport {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Earthquake")
        private Earthquake earthquake;

        @JacksonXmlProperty(localName = "Comments")
        private Comment comments;

        public Earthquake getEarthquake() {
            return this.earthquake;
        }

        public Comment getComment() {
            return this.comments;
        }

        public static class Earthquake {

            @JacksonXmlProperty(localName = "OriginTime")
            private Instant originTime;

            @JacksonXmlProperty(localName = "ArrivalTime")
            private Instant arrivalTime;

            @JacksonXmlProperty(localName = "Hypocenter")
            private Hypocenter hypocenter;

            @JacksonXmlProperty(localName = "Magnitude")
            private Magnitude magnitude;

            public Instant getOriginTime() {
                return this.originTime;
            }

            public Instant getArrivalTime() {
                return this.arrivalTime;
            }

            public Hypocenter getHypocenter() {
                return this.hypocenter;
            }

            public Magnitude getMagnitude() {
                return this.magnitude;
            }

            public static class Hypocenter {

                @JacksonXmlProperty(localName = "Area")
                private Area area;

                public Area getArea() {
                    return this.area;
                }

                public static class Area {

                    @JacksonXmlProperty(localName = "Name")
                    private String name;

                    @JacksonXmlProperty(localName = "Code")
                    private String code;

                    @JacksonXmlProperty(localName = "Coordinate")
                    private Coordinate coordinate;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public Coordinate getCoordinate() {
                        return this.coordinate;
                    }

                    @Override
                    public String toString() {
                        return "Area{" +
                                "name='" + this.name + '\'' +
                                ", code='" + this.code + '\'' +
                                ", coordinate='" + this.coordinate + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "Hypocenter{" +
                            "area=" + this.area +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Earthquake{" +
                        "originTime='" + this.originTime + '\'' +
                        ", arrivalTime='" + this.arrivalTime + '\'' +
                        ", hypocenter=" + this.hypocenter +
                        ", magnitude='" + this.magnitude + '\'' +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Body{" +
                    "earthquake=" + this.earthquake +
                    ", comments=" + this.comments +
                    '}';
        }

    }

    @Override
    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        return null;
    }

    @Override
    public String toString() {
        return "VXSE52{" +
                "body=" + this.body +
                ", control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
