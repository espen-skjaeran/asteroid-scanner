package com.harper.asteroids;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harper.asteroids.model.NearEarthObject;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Receives a set of neo ids and rates them after earth proximity.
 * Retrieves the approach data for them and sorts to the n closest.
 * https://api.nasa.gov/neo/rest/v1/neo/
 * Alerts if someone is possibly hazardous.
 */
public class ApproachDetector {
    private static final String NEO_URL = "https://api.nasa.gov/neo/rest/v1/neo/";
    private List<String> nearEarthObjectIds;
    private Client client;
    private ObjectMapper mapper;

    public ApproachDetector(List<String> ids) {
        this.nearEarthObjectIds = ids;
        this.client = ClientBuilder.newClient();
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Get the n closest approaches in this period
     * @param limit - n
     */
    public List<NearEarthObject> getClosestApproaches(int limit) {
        List<NearEarthObject> neos = new ArrayList<>(limit);
        List<CompletableFuture<NearEarthObject>> completableFutures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);// it is depend on where our APP is running
        for(String id: nearEarthObjectIds) {
            CompletableFuture<NearEarthObject> requestCompletableFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return getNearEarthObject(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }, executorService);
            completableFutures.add(requestCompletableFuture);
        }
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
        neos = completableFutures.stream()
                .map(CompletableFuture::join).collect(Collectors.toList());

        executorService.shutdown();
        System.out.println("Received " + neos.size() + " neos, now sorting");

        return getClosest(neos, limit);
    }

    public NearEarthObject getNearEarthObject(String id) throws IOException {

            System.out.println("Check passing of object " + id);
            Response response = client
                    .target(NEO_URL + id)
                    .queryParam("api_key", App.API_KEY)
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            NearEarthObject neo = mapper.readValue(response.readEntity(String.class), NearEarthObject.class);
            return neo;
    }

    /**
     * Get the closest passing.
     * @param neos the NearEarthObjects
     * @param limit
     * @return
     */
    public static List<NearEarthObject> getClosest(List<NearEarthObject> neos, int limit) {
        //TODO: Should ignore the passes that are not today/this week.
        return neos.stream()
                .filter(neo -> neo.getCloseApproachData() != null && ! neo.getCloseApproachData().isEmpty())
                .sorted(new VicinityComparator())
                .limit(limit)
                .collect(Collectors.toList());
    }

}
