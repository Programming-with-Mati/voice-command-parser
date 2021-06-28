package com.github.programmingwithmati.voice.consumer;

import com.github.programmingwithmati.voice.VoiceParserTopology;
import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;
import com.github.programmingwithmati.voice.serdes.JsonSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Serdes;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ParsedCommandConsumer {

    public static void main(String[] args) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092",
                ConsumerConfig.GROUP_ID_CONFIG, "parsed-command-consumer-1",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                );

        try (var commandConsumer = new KafkaConsumer<>(props, Serdes.String().deserializer(), new JsonSerde<>(ParsedVoiceCommand.class).deserializer())) {
            commandConsumer.subscribe(List.of(VoiceParserTopology.RECOGNIZED_COMMANDS_TOPIC, VoiceParserTopology.UNRECOGNIZED_COMMAND_TOPIC));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> close(commandConsumer)));

            while (true)  {
                commandConsumer.poll(Duration.ofSeconds(1))
                        .forEach(record -> System.out.println("""
                            Topic: %s
                            Result: %s
                            """.formatted(record.topic(), record.value().toString())));
                commandConsumer.commitAsync();
            }
        }

    }

    private static void close(KafkaConsumer<String, ParsedVoiceCommand> commandConsumer) {
        commandConsumer.wakeup();
    }
}
