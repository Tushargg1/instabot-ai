package com.example.instagramreelai.job;

public final class JobStatus {
    public static final String RECEIVED = "RECEIVED";
    public static final String QUEUED = "QUEUED";
    public static final String CLAIMED = "CLAIMED";
    public static final String DOWNLOADING = "DOWNLOADING";
    public static final String EXTRACTING_AUDIO = "EXTRACTING_AUDIO";
    public static final String TRANSCRIBING = "TRANSCRIBING";
    public static final String SUMMARIZING = "SUMMARIZING";
    public static final String SAVING = "SAVING";
    public static final String REPLYING = "REPLYING";
    public static final String COMPLETED = "COMPLETED";

    // Failure states
    public static final String MEDIA_UNAVAILABLE = "MEDIA_UNAVAILABLE";
    public static final String TRANSCRIPTION_FAILED = "TRANSCRIPTION_FAILED";
    public static final String SUMMARY_FAILED = "SUMMARY_FAILED";
    public static final String REPLY_FAILED = "REPLY_FAILED";
    public static final String FAILED = "FAILED";
    public static final String UNKNOWN_JOB_TYPE = "UNKNOWN_JOB_TYPE";

    private JobStatus() {}
}
