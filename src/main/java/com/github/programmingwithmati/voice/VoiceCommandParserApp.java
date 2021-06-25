package com.github.programmingwithmati.voice;

import com.github.programmingwithmati.voice.configuration.StreamsConfiguration;
import com.github.programmingwithmati.voice.service.MockSttClient;
import com.github.programmingwithmati.voice.service.MockTranslateClient;
import org.apache.kafka.streams.KafkaStreams;

public class VoiceCommandParserApp {

    public static void main(String[] args) {
        var streamsConfiguration = new StreamsConfiguration();
        var voiceParserTopology = new VoiceParserTopology(new MockSttClient(), new MockTranslateClient());

        var kafkaStreams = new KafkaStreams(voiceParserTopology.createTopology(), streamsConfiguration.streamsConfiguration());

        kafkaStreams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }

}
