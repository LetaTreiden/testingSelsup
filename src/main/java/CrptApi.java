import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CrptApi {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final int reqLim;
    private final Duration timeUnitDuration;
    private final String authToken;
    private int reqCount;
    private long nextReset;

    public CrptApi(TimeUnit timeUnit, int reqLim, String authToken) {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.reqLim = reqLim;
        this.timeUnitDuration = Duration.ofMillis(timeUnit.toMillis(1));
        this.authToken = authToken;
        this.reqCount = 0;
        this.nextReset = System.currentTimeMillis() + timeUnitDuration.toMillis();

        this.scheduler.scheduleAtFixedRate(this::resetRequests, 0, timeUnit.toMillis(1),
                TimeUnit.MILLISECONDS);
    }

    private synchronized void resetRequests() {
        reqCount = 0;
        nextReset = System.currentTimeMillis() + timeUnitDuration.toMillis();
        notifyAll();
    }

    public HttpResponse<String> createDoc(Object document, String signature) throws IOException,
            InterruptedException {
        synchronized (this) {
            while (reqCount >= reqLim) {
                long waitTime = nextReset - System.currentTimeMillis();
                if (waitTime > 0) {
                    wait(waitTime);
                } else {
                    resetRequests();
                }
            }
            reqCount++;
        }

        String jsonBody = mapper.writeValueAsString(document);
        ObjectNode reqBody = mapper.createObjectNode();
        reqBody.putPOJO("desc", document);
        reqBody.put("sign", signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    public static void main(String[] args) {
        try {
            String authToken = "api";
            CrptApi api = new CrptApi(TimeUnit.MINUTES, 9, authToken);
            Object document = new Object() {
                public String participantInn = "111111111111";
                public String doc_id = "111111";
                public String doc_status = "NEW";
                public String doc_type = "LP_INTRODUCE_GOODS";
                public boolean importRequest = true;
                public String owner_inn = "222222222";
                public String producer_inn = "333333333";
                public String production_date = "2024-06-03";
                public String production_type = "DOMESTIC";
                public Product[] products = new Product[]{
                        new Product("certDoc", "2024-06-03",
                                "cert123", "00000000", "22222222",
                                "2024-06-03", "1234", "uitCode", "uituCode")
                };
                public String reg_date = "2024-06-03";
                public String reg_number = "reg number 1 2 3 4 5 6 7 8 9";
            };
            String signature = "sign";

            HttpResponse<String> response = api.createDoc(document, signature);
            System.out.println(response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class Product {
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;

        public Product(String certificate_document, String certificate_document_date,
                       String certificate_document_number, String owner_inn, String producer_inn,
                       String production_date, String tnved_code, String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }
    }
}
