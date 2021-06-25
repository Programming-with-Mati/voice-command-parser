package com.github.programmingwithmati.voice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCommand {

    private String id;
    private byte[] audio;
    private String audioCodec;
    private String language;
}
