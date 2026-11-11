package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.RequestSigner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class BinanceRequestSigner implements RequestSigner {

  private static final String ALGORITHM = "Ed25519";
  private static final String PEM_LINE = "-----";

  private final PrivateKey privateKey;

  BinanceRequestSigner(PrivateKey privateKey) {
    this.privateKey = privateKey;
  }

  static BinanceRequestSigner fromPemFile(Path pemFile) {
    try {
      String base64Key =
          Files.readAllLines(pemFile, StandardCharsets.US_ASCII).stream()
              .filter(line -> !line.startsWith(PEM_LINE))
              .map(String::strip)
              .reduce("", String::concat);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64Key));
      return new BinanceRequestSigner(KeyFactory.getInstance(ALGORITHM).generatePrivate(keySpec));
    } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "Could not read the Ed25519 private key (PKCS#8 PEM) from " + pemFile, e);
    }
  }

  @Override
  public String sign(String payload) {
    try {
      Signature signature = Signature.getInstance(ALGORITHM);
      signature.initSign(privateKey);
      signature.update(payload.getBytes(StandardCharsets.US_ASCII));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the Binance request", e);
    }
  }
}
