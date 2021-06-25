package com.github.programmingwithmati.voice.service;

import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;

public interface TranslateClient {

    ParsedVoiceCommand translate(ParsedVoiceCommand original);
}
