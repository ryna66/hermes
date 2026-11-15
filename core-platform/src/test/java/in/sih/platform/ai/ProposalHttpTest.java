package in.sih.platform.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProposalHttpTest {
    @Test void draftUsesContractAndRejectsMalformedAndUnavailableResponses() throws Exception {
        var body=new AtomicReference<>("{\"proposal\":\"Survey and pilot\",\"confidence\":0.9}");
        var received=new AtomicReference<String>();
        var upgrade=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/draft-proposal", e->{upgrade.set(e.getRequestHeaders().getFirst("Upgrade"));received.set(new String(e.getRequestBody().readAllBytes()));byte[] bytes=body.get().getBytes();e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        var client=new HttpAiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofMillis(500));
        try {
            assertEquals("Survey and pilot",client.draftProposal("Water","Unsafe","HEALTH","University",List.of("water")).proposal());
            assertNull(upgrade.get(),"Uvicorn requests must not attempt an h2c upgrade");
            var request=new ObjectMapper().readTree(received.get());assertEquals("Water",request.get("challenge").get("title").asText());assertEquals("University",request.get("university").get("name").asText());
            for(String invalid:List.of("{}","{\"proposal\":\"\",\"confidence\":0.9}","{\"proposal\":\"test\",\"confidence\":2}","garbage")) {
                body.set(invalid);assertThrows(IllegalStateException.class,()->client.draftProposal("a","b","OTHER","u",List.of()));
            }
        } finally {server.stop(0);}
        assertThrows(IllegalStateException.class,()->client.draftProposal("a","b","OTHER","u",List.of()));
    }
}
