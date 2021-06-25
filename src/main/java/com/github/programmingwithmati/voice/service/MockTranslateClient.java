package com.github.programmingwithmati.voice.service;

import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;

public class MockTranslateClient implements TranslateClient {

    public ParsedVoiceCommand translate(ParsedVoiceCommand original) {
        return ParsedVoiceCommand.builder()
                .id(original.getId())
                .textCommand("call juan")
                .probability(original.getProbability())
                .language(original.getLanguage())
                .build();
    }
}
