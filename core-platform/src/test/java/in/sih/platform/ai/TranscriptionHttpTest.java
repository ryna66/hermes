package in.sih.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptionHttpTest {
    @Test void sendsPythonContractAndHandlesUnavailableService() throws Exception {
        var received=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/transcribe",e->{
            received.set(new String(e.getRequestBody().readAllBytes()));
            byte[] bytes="{\"text\":\"Water issue\",\"confidence\":0.9,\"language\":\"en\"}".getBytes();
            e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
        });
        server.start();
        var client=new HttpAiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofMillis(500));
        try {
            assertEquals("Water issue",client.transcribe("YQ==","audio/wav","en").text());
            var request=new ObjectMapper().readTree(received.get());
            assertEquals("YQ==",request.get("audio_base64").asText());
            assertEquals("audio/wav",request.get("mime_type").asText());
            assertEquals("en",request.get("language").asText());
        } finally {server.stop(0);}
        assertThrows(IllegalStateException.class,()->client.transcribe("YQ==","audio/wav","en"));
    }
}
