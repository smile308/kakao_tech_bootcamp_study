package kr.adapterz.springdatajpa.service;

public interface ViewCountUpdater {

    long increment(Long postId, long baselineViewCount);
}
