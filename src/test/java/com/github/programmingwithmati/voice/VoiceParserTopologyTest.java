package com.github.programmingwithmati.voice;

import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;
import com.github.programmingwithmati.voice.model.VoiceCommand;
import com.github.programmingwithmati.voice.serdes.JsonSerde;
import com.github.programmingwithmati.voice.service.SttClient;
import com.github.programmingwithmati.voice.service.TranslateClient;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoiceParserTopologyTest {

    TopologyTestDriver testDriver;
    private TestInputTopic<String, VoiceCommand> voiceCommandsTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> recognizedCommandsOutputTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> unrecognizedCommandsOutputTopic;

    @Mock
    SttClient sttClient;
    @Mock
    TranslateClient translateClient;
    @InjectMocks
    VoiceParserTopology voiceParserTopology;

    @BeforeEach
    void setup() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        testDriver = new TopologyTestDriver(voiceParserTopology.createTopology(), props);

        var voiceCommandJsonSerde = new JsonSerde<>(VoiceCommand.class);
        var parsedVoiceCommandJsonSerde = new JsonSerde<>(ParsedVoiceCommand.class);

        voiceCommandsTopic = testDriver.createInputTopic(VoiceParserTopology.VOICE_COMMANDS_TOPIC, Serdes.String().serializer(), voiceCommandJsonSerde.serializer());
        recognizedCommandsOutputTopic = testDriver.createOutputTopic(VoiceParserTopology.RECOGNIZED_COMMANDS_TOPIC, Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());
        unrecognizedCommandsOutputTopic = testDriver.createOutputTopic(VoiceParserTopology.UNRECOGNIZED_COMMAND_TOPIC, Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());
    }

    @Test
    void testWhenEnglishDataParsedCorrectly() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var id = UUID.randomUUID().toString();
        var data = VoiceCommand.builder()
                .id(id)
                .audio(bytes)
                .language("en-US")
                .audioCodec("FLAC")
                .build();

        var parsedVoiceCommand = ParsedVoiceCommand
                .builder()
                .textCommand("call john")
                .language("en-US")
                .id(id)
                .probability(0.95)
                .build();

        given(sttClient.speechToText(data)).willReturn(parsedVoiceCommand);

        voiceCommandsTopic.pipeInput(id, data);
        var actualVoiceCommand = recognizedCommandsOutputTopic.readRecord().getValue();

        assertTrue(unrecognizedCommandsOutputTopic.isEmpty());
        assertEquals(id, actualVoiceCommand.getId());
        assertEquals(parsedVoiceCommand.getTextCommand(), actualVoiceCommand.getTextCommand());

        verify(translateClient, never()).translate(any(ParsedVoiceCommand.class));

    }

    @Test
    void testWhenSpanishDataParsedCorrectly() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var id = UUID.randomUUID().toString();
        var data = VoiceCommand.builder()
                .id(id)
                .audio(bytes)
                .language("en-US")
                .audioCodec("FLAC")
                .build();

        var parsedVoiceCommand = ParsedVoiceCommand
                .builder()
                .textCommand("llamar a juan")
                .language("es-AR")
                .id(id)
                .probability(0.95)
                .build();

        given(sttClient.speechToText(data)).willReturn(parsedVoiceCommand);
        given(translateClient.translate(parsedVoiceCommand)).willReturn(parsedVoiceCommand.toBuilder().textCommand("call juan").language("en-US").build());

        voiceCommandsTopic.pipeInput(id, data);
        var actualVoiceCommand = recognizedCommandsOutputTopic.readRecord().getValue();

        assertTrue(unrecognizedCommandsOutputTopic.isEmpty());
        assertEquals(id, actualVoiceCommand.getId());
        assertEquals("call juan", actualVoiceCommand.getTextCommand());

    }

    @Test
    void testWhenNotRecognized() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var id = UUID.randomUUID().toString();
        var data = VoiceCommand.builder()
                .id(id)
                .audio(bytes)
                .language("en-US")
                .audioCodec("FLAC")
                .build();

        var parsedVoiceCommand = ParsedVoiceCommand
                .builder()
                .textCommand("llamar a juan")
                .language("es-AR")
                .id(id)
                .probability(0.30)
                .build();

        given(sttClient.speechToText(data)).willReturn(parsedVoiceCommand);

        voiceCommandsTopic.pipeInput(id, data);
        var actualVoiceCommand = unrecognizedCommandsOutputTopic.readRecord().getValue();

        assertTrue(recognizedCommandsOutputTopic.isEmpty());
        assertEquals(id, actualVoiceCommand.getId());
        verify(translateClient, never()).translate(any(ParsedVoiceCommand.class));

    }

    @Test
    void testWhenAudioTooShortFiltered() {
        var bytes = new byte[9];
        new Random().nextBytes(bytes);
        var id = UUID.randomUUID().toString();
        var data = VoiceCommand.builder()
                .id(id)
                .audio(bytes)
                .language("en-US")
                .audioCodec("FLAC")
                .build();

        voiceCommandsTopic.pipeInput(id, data);

        assertTrue(recognizedCommandsOutputTopic.isEmpty());
        assertTrue(unrecognizedCommandsOutputTopic.isEmpty());
        verify(translateClient, never()).translate(any(ParsedVoiceCommand.class));
        verify(sttClient, never()).speechToText(any(VoiceCommand.class));

    }

}
