package io.quarkus.houseelves.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api/state")
public class StateResource {

    private static final Path STATE_PATH = Path.of(System.getProperty("user.home"),
            ".config", "github-worker", "state.json");

    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getState() {
        if (!Files.exists(STATE_PATH)) {
            return Response.ok("{\"issues\":{},\"reviews\":{}}").build();
        }
        try {
            String json = Files.readString(STATE_PATH);
            return Response.ok(json).build();
        } catch (IOException e) {
            return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/{key}/retry")
    @Produces(MediaType.APPLICATION_JSON)
    public Response retry(@PathParam("key") String key) {
        // key comes in as owner_repo#number (slashes encoded), decode it
        key = key.replace("_", "/").replaceFirst("/", "_").replace("_", "/");

        try {
            ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(STATE_PATH));
            ObjectNode issues = (ObjectNode) root.get("issues");
            ObjectNode reviews = (ObjectNode) root.get("reviews");

            if (issues != null && issues.has(key)) {
                ObjectNode entry = (ObjectNode) issues.get(key);
                String currentState = entry.get("state").asText();

                // Reset to a retryable state
                String newState = switch (currentState) {
                    case "AWAITING_APPROVAL" -> "NEW";
                    case "CODING" -> "CODING";
                    case "SELF_REVIEWING" -> "CODING";
                    case "FIXING_REVIEW" -> "SELF_REVIEWING";
                    case "ADDRESSING_FEEDBACK" -> "READY_FOR_REVIEW";
                    case "SQUASHING" -> "READY_FOR_REVIEW";
                    case "MONITORING_CI" -> "SQUASHING";
                    case "FIXING_CI" -> "SQUASHING";
                    case "DONE" -> "NEW";
                    default -> currentState;
                };

                entry.put("state", newState);
                entry.put("lastUpdated", Instant.now().toString());

                saveState(root);
                return Response.ok(Map.of("status", "reset", "key", key,
                        "from", currentState, "to", newState)).build();
            }

            if (reviews != null && reviews.has(key)) {
                ObjectNode entry = (ObjectNode) reviews.get(key);
                String currentState = entry.get("state").asText();
                entry.put("state", "NEW");
                entry.put("lastUpdated", Instant.now().toString());

                saveState(root);
                return Response.ok(Map.of("status", "reset", "key", key,
                        "from", currentState, "to", "NEW")).build();
            }

            return Response.status(404).entity(Map.of("error", "Key not found: " + key)).build();

        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @jakarta.ws.rs.Path("/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response remove(@PathParam("key") String key) {
        key = key.replace("_", "/").replaceFirst("/", "_").replace("_", "/");

        try {
            ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(STATE_PATH));
            ObjectNode issues = (ObjectNode) root.get("issues");
            ObjectNode reviews = (ObjectNode) root.get("reviews");

            boolean removed = false;
            if (issues != null && issues.has(key)) {
                issues.remove(key);
                removed = true;
            }
            if (reviews != null && reviews.has(key)) {
                reviews.remove(key);
                removed = true;
            }

            if (!removed) {
                return Response.status(404).entity(Map.of("error", "Key not found: " + key)).build();
            }

            saveState(root);
            return Response.ok(Map.of("status", "removed", "key", key)).build();

        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    private void saveState(JsonNode root) throws IOException {
        Path tmp = STATE_PATH.resolveSibling("state.json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
        Files.move(tmp, STATE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
