package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinanceRequestSignerTest {

  private static final String PAYLOAD = "symbol=BTCUSDT&side=BUY&timestamp=1762779782000";
  private static final String NOT_A_KEY =
      "-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n-----END PRIVATE KEY-----\n";

  @TempDir Path directory;

  @Test
  void signsWithTheKeyFromAPemFile() throws IOException, GeneralSecurityException {
    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Path pemFile = directory.resolve("private.pem");
    Files.writeString(pemFile, pem(keyPair), StandardCharsets.US_ASCII);

    String signature = BinanceRequestSigner.fromPemFile(pemFile).sign(PAYLOAD);

    Signature verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
    assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
  }

  @Test
  void refusesAFileThatIsNotAnEd25519Key() throws IOException {
    Path pemFile = directory.resolve("broken.pem");
    Files.writeString(pemFile, NOT_A_KEY, StandardCharsets.US_ASCII);

    assertThatThrownBy(() -> BinanceRequestSigner.fromPemFile(pemFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("broken.pem");
  }

  @Test
  void refusesAMissingFile() {
    Path missing = directory.resolve("missing.pem");

    assertThatThrownBy(() -> BinanceRequestSigner.fromPemFile(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing.pem");
  }

  private static String pem(KeyPair keyPair) {
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
  }
}
