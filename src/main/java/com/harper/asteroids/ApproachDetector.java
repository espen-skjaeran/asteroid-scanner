package com.harper.asteroids;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harper.asteroids.model.CloseApproachData;
import com.harper.asteroids.model.NearEarthObject;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private ObjectMapper mapper; // = new ObjectMapper();

    public ApproachDetector(ObjectMapper mapper, List<String> ids) {
        this.nearEarthObjectIds = ids;
        this.client = ClientBuilder.newClient();
        this.mapper = mapper;
    }

    /**
     * Get the n closest approaches in this period
     * @param limit - n
     */
    public List<NearEarthObject> getClosestApproaches(int limit) {
        List<NearEarthObject> neos = new ArrayList<>(limit);
        for(String id: nearEarthObjectIds) {
            try {
                System.out.println("Check passing of object " + id);
                Response response = client
                    .target(NEO_URL + id)
                    .queryParam("api_key", App.API_KEY)
                    .request(MediaType.APPLICATION_JSON)
                    .get();

                NearEarthObject neo = mapper.readValue(response.readEntity(String.class), NearEarthObject.class);
                neos.add(neo);
            } catch (IOException e) {
                System.err.println("Failed scanning for asteroids: " + e);
            }
        }
        System.out.println("Received " + neos.size() + " neos, now sorting");

        return getClosest(neos, limit);
    }

    public List<NearEarthObject> getClosestApproachesVer2(int limit) {

        List<NearEarthObject> neos = new ArrayList<>(limit);

        CompletableFuture<List<String>> neosIds = CompletableFuture.supplyAsync(() -> nearEarthObjectIds);
        neosIds.thenCompose(ids -> {

            List<CompletableFuture<NearEarthObject>> completionStages = ids.stream().map(id -> getNeo(id)).collect(Collectors.toList());

            return CompletableFuture.allOf(completionStages.toArray(new CompletableFuture[completionStages.size()]))
                    .thenApply(v -> completionStages.stream()
                            .map(CompletableFuture::join).collect(Collectors.toList()));

        }).whenComplete((nearEarthObjects, throwable) -> {
            if(throwable == null) {
                neos.addAll(nearEarthObjects);
            } else {
                throw new RuntimeException(throwable);
            }
        }).join();

        return getClosest(neos, limit);

    }

    private CompletableFuture<NearEarthObject> getNeo(String id) {
        return CompletableFuture.supplyAsync(() -> {
            NearEarthObject neo = null;
            try {
                System.out.println("Check passing of object " + id);
                Response response = client
                        .target(NEO_URL + id)
                        .queryParam("api_key", App.API_KEY)
                        .request(MediaType.APPLICATION_JSON)
                        .get();

                neo = mapper.readValue(response.readEntity(String.class), NearEarthObject.class);

            } catch (IOException e) {
                System.err.println("Failed scanning for asteroids: " + e);
            }
            return neo;
        });
    }

    /**
     * Get the closest passing.
     * @param neos the NearEarthObjects
     * @param limit
     * @return
     */
    public static List<NearEarthObject> getClosest(List<NearEarthObject> neos, int limit) {
        //TODO: Should ignore the passes that are not today/this week.
        Map<NearEarthObject, List<CloseApproachData>> dataMap = new HashMap<>();

        neos.stream().filter(neo -> neo.getCloseApproachData() != null && ! neo.getCloseApproachData().isEmpty())
                .forEach(nearEarthObject -> {
                    dataMap.put(nearEarthObject, nearEarthObject.getCloseApproachData().stream()
                            .filter(ApproachDetector::isPassingThisWeek).collect(Collectors.toList()));
                });

        neos.forEach(nearEarthObject -> {
            nearEarthObject.getCloseApproachData().clear();
            nearEarthObject.getCloseApproachData().addAll(dataMap.get(nearEarthObject));
        });

        return neos.stream()
                .sorted(new VicinityComparator())
                .limit(limit)
                .collect(Collectors.toList());
    }

    static boolean isPassingThisWeek(CloseApproachData cad) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.with(DayOfWeek.MONDAY);
        LocalDate endDate = today.with(DayOfWeek.SUNDAY);

        return cad.getCloseApproachDate().before(asDate(endDate)) &&
                cad.getCloseApproachDate().after(asDate(startDate));
    }

    static Date asDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

}
