package com.xowns.celfeed.common.event;

public abstract class KafkaTopic {
    public static final String LIKE_POST = "like-post";
    public static final String LIKE_POST_DLT = "like-post.DLT";
    public static final String WRITE_POST = "write-post";
    public static final String WRITE_POST_DTL = "write-post.DTL";
    public static final String WRITE_POST_FANOUT = "write-post-fanout";
    public static final String WRITE_POST_FANOUT_DLT = "write-post-fanout.DLT";
}
