package com.ai.assistant.service;

/** Another Brewly crawl is still running, so this crawl was not started. */
public class CrawlAlreadyRunningException extends RuntimeException {

  public CrawlAlreadyRunningException() {
    super("A Brewly website crawl is already running");
  }
}
