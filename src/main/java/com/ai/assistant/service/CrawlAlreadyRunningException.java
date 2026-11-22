package com.ai.assistant.service;

/** Another crawl of the same tenant is still running, so this crawl was not started. */
public class CrawlAlreadyRunningException extends RuntimeException {

  public CrawlAlreadyRunningException() {
    super("A website crawl is already running for this tenant");
  }
}
