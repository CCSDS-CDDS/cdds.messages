package cdds.service.tm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/** Throwaway check that the Java validator rejects non-compliant JSON. */
public class ScratchSchemaCheck {

    @Test
    public void checkNegativeCases() throws Exception {
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        final JsonSchema schema = factory.getSchema(
                Files.readString(Path.of("grpc", "cdds-tm-service-configuration.schema.json"), StandardCharsets.UTF_8));
        final ObjectMapper mapper = new ObjectMapper();

        // both oneof branches present -> must be rejected
        final JsonNode both = mapper.readTree("{"
            + "\"gRpcServer\":{\"serviceProvider\":\"p\",\"address\":\"a\",\"port\":1},"
            + "\"endpoints\":{\"endpoints\":[{"
            + "\"serviceVersion\":1,\"serviceProvider\":\"p\",\"serviceUser\":\"s\","
            + "\"frameQuality\":\"GOOD\",\"gvcIds\":{\"gvcId\":[]},\"qos\":\"GOOD\"}]}}");
        final Set<ValidationMessage> bothErrors = schema.validate(both);
        System.out.println("ScratchSchemaCheck: both oneof branches -> " + bothErrors.size() + " error(s): " + bothErrors);

        // invalid enum + unknown property -> must be rejected
        final JsonNode bad = mapper.readTree("{"
            + "\"gRpcServer\":{\"serviceProvider\":\"p\",\"address\":\"a\",\"port\":1},"
            + "\"endpoints\":{\"endpoints\":[{"
            + "\"serviceVersion\":1,\"serviceProvider\":\"p\",\"serviceUser\":\"s\","
            + "\"surprise\":1,\"qos\":\"BEST_EFFORT\"}]}}");
        final Set<ValidationMessage> badErrors = schema.validate(bad);
        System.out.println("ScratchSchemaCheck: unknown property + bad enum -> " + badErrors.size() + " error(s): " + badErrors);
    }
}
