package xyzTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import coreUtils.library.media.MediaSpeedProcessor;
import coreUtils.library.media.SubtitleSpeedScaler;
import dataRepo.dbManager.PocketBaseClient;

/**
 * Unit tests verifying FFmpeg atempo filter chaining, voice clarity boost filter generation,
 * SRT subtitle timestamp speed-scaling, and PocketBase filter injection sanitization.
 */
public class SpeedProcessorAndSecurityTest {

    @Test
    public void testAtempoFilterChainWithinSingleStage() {
        assertEquals("anull", MediaSpeedProcessor.buildAtempoFilterChain(1.0f));
        assertEquals("atempo=1.5000", MediaSpeedProcessor.buildAtempoFilterChain(1.5f));
        assertEquals("atempo=2.0000", MediaSpeedProcessor.buildAtempoFilterChain(2.0f));
    }

    @Test
    public void testAtempoFilterChainMultiStageHighAndLowSpeeds() {
        assertEquals("atempo=2.0,atempo=1.5000", MediaSpeedProcessor.buildAtempoFilterChain(3.0f));
        assertEquals("atempo=0.5,atempo=0.5000", MediaSpeedProcessor.buildAtempoFilterChain(0.25f));
    }

    @Test
    public void testAudioFilterChainWithPitchSafeVoiceBoost() {
        assertEquals("atempo=1.5000,dynaudnorm=f=150:g=15",
                MediaSpeedProcessor.buildAudioFilterChain(1.5f, true));
        assertEquals("dynaudnorm=f=150:g=15",
                MediaSpeedProcessor.buildAudioFilterChain(1.0f, true));
    }

    @Test
    public void testSubtitleSpeedScalingHalvesTimestampsAt2x() throws Exception {
        File inSub = File.createTempFile("test_in", ".srt");
        File outSub = File.createTempFile("test_out", ".srt");
        try {
            String srt = "1\n00:01:00,000 --> 00:02:00,000\nHello World\n";
            Files.write(inSub.toPath(), srt.getBytes(StandardCharsets.UTF_8));
            assertTrue(SubtitleSpeedScaler.scaleSubtitleFile(inSub, outSub, 2.0f));
            String result = new String(Files.readAllBytes(outSub.toPath()), StandardCharsets.UTF_8);
            assertTrue(result.contains("00:00:30,000 --> 00:01:00,000"));
        } finally {
            inSub.delete();
            outSub.delete();
        }
    }

    @Test
    public void testPocketBaseFilterSanitizationEscapesQuotes() {
        String malicious = "abc' || id != '";
        String sanitized = PocketBaseClient.sanitizeFilterValue(malicious);
        assertFalse(sanitized.contains(" '"));
        assertTrue(sanitized.contains("\\'"));
    }
}
