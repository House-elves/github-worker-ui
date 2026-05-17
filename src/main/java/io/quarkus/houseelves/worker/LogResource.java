package io.quarkus.houseelves.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api/logs")
public class LogResource {

    private static final Path CLAUDE_LOG = Path.of(System.getProperty("user.home"),
            ".config", "github-worker", "claude.log");

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLogs(@QueryParam("lines") @DefaultValue("100") int lines) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "journalctl", "--user-unit", "github-worker", "--no-pager",
                    "-n", String.valueOf(lines), "--output", "short-iso");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            String output;
            if (finished) {
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } else {
                process.destroyForcibly();
                output = "Log fetch timed out";
            }
            return Response.ok(Map.of("logs", output)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/claude")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClaudeLogs(@QueryParam("lines") @DefaultValue("50") int lines) {
        if (!Files.exists(CLAUDE_LOG)) {
            return Response.ok(Map.of("logs", "No Claude log yet")).build();
        }
        try {
            List<String> allLines = Files.readAllLines(CLAUDE_LOG);
            int start = Math.max(0, allLines.size() - lines);
            String output = String.join("\n", allLines.subList(start, allLines.size()));
            return Response.ok(Map.of("logs", output)).build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
