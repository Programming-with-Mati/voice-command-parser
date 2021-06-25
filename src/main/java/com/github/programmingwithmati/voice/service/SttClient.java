package com.github.programmingwithmati.voice.service;

import com.github.programmingwithmati.voice.model.ParsedVoiceCommand;
import com.github.programmingwithmati.voice.model.VoiceCommand;

public interface SttClient {

    ParsedVoiceCommand speechToText(VoiceCommand value);
}
