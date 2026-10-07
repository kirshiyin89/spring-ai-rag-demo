/*
package com.ai.assistant.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private final EmbeddingModel embeddingModel;

  public EmbeddingService(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public void testEmbedding() {

    String text1 = "How do I request a company laptop?";
    String text2 = "Where can I get my work computer?";
    String text3 = "What is the capital of France";

    float[] embedding1 = embeddingModel.embed(text1);
    float[] embedding2 = embeddingModel.embed(text2);
    float[] embedding3 = embeddingModel.embed(text3);

    System.out.println("Vector 1 size: " + embedding1.length);
    System.out.println("Vector 2 size: " + embedding2.length);
    System.out.println("Vector 3 size: " + embedding3.length);

    double similarity12 = cosineSimilarity(embedding1, embedding2);
    double similarity13 = cosineSimilarity(embedding1, embedding3);

    System.out.println("Laptop ↔ work computer: " + similarity12);
    System.out.println("Laptop ↔ VPN: " + similarity13);
  }

  private double cosineSimilarity(float[] a, float[] b) {

    double dotProduct = 0;
    double magnitudeA = 0;
    double magnitudeB = 0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      magnitudeA += a[i] * a[i];
      magnitudeB += b[i] * b[i];
    }

    return dotProduct /
        (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
  }
}*/
