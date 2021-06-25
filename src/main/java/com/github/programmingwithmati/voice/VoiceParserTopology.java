package com.github.programmingwithmati.voice;

import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;
import com.github.programmingwithmati.voice.model.VoiceCommand;
import com.github.programmingwithmati.voice.serdes.JsonSerde;
import com.github.programmingwithmati.voice.service.SttClient;
import com.github.programmingwithmati.voice.service.TranslateClient;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

import java.util.Map;

public class VoiceParserTopology {

    public static final double THRESHOLD = 0.85;
    public static final String VOICE_COMMANDS_TOPIC = "voice-commands";
    public static final String UNRECOGNIZED_COMMAND_TOPIC = "unrecognized-commands";
    public static final String RECOGNIZED_COMMANDS_TOPIC = "recognized-commands";
    private final SttClient sttClient;
    private final TranslateClient translateClient;

    public VoiceParserTopology(SttClient sttClient, TranslateClient translateClient) {
        this.sttClient = sttClient;
        this.translateClient = translateClient;
    }

    public Topology createTopology() {

        StreamsBuilder streamsBuilder = new StreamsBuilder();
        // Create Serdes
        JsonSerde<VoiceCommand> voiceCommandJsonSerde = new JsonSerde<>(VoiceCommand.class);
        JsonSerde<ParsedVoiceCommand> parsedVoiceCommandJsonSerde = new JsonSerde<>(ParsedVoiceCommand.class);

        Map<String, KStream<String, ParsedVoiceCommand>> branchesMap = streamsBuilder.stream(VOICE_COMMANDS_TOPIC, Consumed.with(Serdes.String(), voiceCommandJsonSerde))
                .filter((key, value) -> value.getAudio().length > 10)
                .mapValues((readOnlyKey, voiceCommand) -> sttClient.speechToText(voiceCommand)) // transform audio to text
                .split(Named.as("branches-")) // split the stream
                .branch((key, voiceCommand) -> voiceCommand.getProbability() > THRESHOLD, Branched.as("recognized")) // If the probability is greater than the threshold, then we have a good sense of security about what the user said
                .defaultBranch(Branched.as("not-recognized")); // If the probability is lower than the threshold, then the STT API is not sure about what the user said

        branchesMap.get("branches-not-recognized")
                .to(UNRECOGNIZED_COMMAND_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde)); // send unrecognized audio to "unrecognized-command" topic

        Map<String, KStream<String, ParsedVoiceCommand>> langStreams = branchesMap.get("branches-recognized")
                .split(Named.as("lang-"))
                .branch((key, voiceCommand) -> voiceCommand.getLanguage().startsWith("en"), Branched.as("en"))
                .defaultBranch(Branched.as("other"));

        langStreams.get("lang-other")
                .mapValues((readOnlyKey, voiceCommand) -> translateClient.translate(voiceCommand)) // Translate non-english voice commands
                .merge(langStreams.get("lang-en")) // merge all commands
                .to(RECOGNIZED_COMMANDS_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde));

        return streamsBuilder.build();

    }
}
