package com.glebzhebrakov.elastictranscoderdemo.rest;

import com.glebzhebrakov.elastictranscoderdemo.service.AudioConversionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class AudioConversionResource {

    private final AudioConversionService audioConversionService;

    @Autowired
    public AudioConversionResource(AudioConversionService audioConversionService) {
        this.audioConversionService = audioConversionService;
    }


    @PostMapping("/rest/api/convert")
    public ResponseEntity<Void> convert( @RequestParam("file") MultipartFile file  ) throws IOException {
        audioConversionService.toMP3( file.getBytes() );
        return ResponseEntity.ok().build();
    }
}
