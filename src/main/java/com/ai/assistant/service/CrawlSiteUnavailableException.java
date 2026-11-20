package com.ai.assistant.service;

/** The start page of a crawl could not be downloaded, so the crawl cannot run. */
public class CrawlSiteUnavailableException extends RuntimeException {

  public CrawlSiteUnavailableException(String message) {
    super(message);
  }
}
