package dev.vestitus.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.vestitus.gate.GateVerdict;
import dev.vestitus.gate.StaticAnalysisGate;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.mcpschema.McpSchemaJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The ADR-002 §4/§5 gate as a REQUIRED TRANSFORM: it reads a verified
 * mcp-schema set, runs the 04b {@link StaticAnalysisGate}, and on PASS emits
 * the canonical stamped envelope that is the ONLY thing the vestitus core
 * loads. A REJECT (or any error) emits nothing loadable. Fail-closed: never
 * exit 0 without a verified Pass + recomputed stamp.
 */
public final class GateCli {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static void main(String[] args) {
        System.exit(new GateCli().run(args, System.in, System.out, System.err));
    }

    int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        try {
            String input = readInput(args, in);
            List<McpSchema> set = parseSet(input);
            GateVerdict v = StaticAnalysisGate.vetAll(set);
            if (v instanceof GateVerdict.Reject r) {
                for (String reason : r.reasons()) {
                    err.println("REJECT: " + reason);
                }
                return 1; // nothing on stdout: nothing loadable
            }
            byte[] canonical = CanonicalJson.canonicalArray(set);
            String canonicalStr = new String(canonical, StandardCharsets.UTF_8);
            String stamp = GateStamp.hmacSha256Hex(canonical);
            out.print("{\"v\":1,\"verdict\":\"PASS\",\"canonical\":"
                + canonicalStr + ",\"stamp\":\"" + stamp + "\"}");
            out.flush();
            return 0;
        } catch (Throwable t) {
            err.println("FAIL-CLOSED: " + t);
            return 2;
        }
    }

    private static String readInput(String[] args, InputStream in)
            throws IOException {
        if (args.length > 0) {
            return Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        }
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static List<McpSchema> parseSet(String input) throws IOException {
        JsonNode root = MAPPER.readTree(input);
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException(
                "input must be a JSON array of mcp-schema documents");
        }
        List<McpSchema> set = new ArrayList<>();
        for (JsonNode element : root) {
            // Hand each element to mcp-schema's strict fail-closed reader so
            // FAIL_ON_UNKNOWN / annotation-completeness still apply per doc.
            set.add(McpSchemaJson.read(MAPPER.writeValueAsString(element)));
        }
        if (set.isEmpty()) {
            throw new IllegalArgumentException("empty mcp-schema set");
        }
        return set;
    }
}
