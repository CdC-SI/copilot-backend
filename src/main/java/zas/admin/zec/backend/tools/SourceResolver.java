package zas.admin.zec.backend.tools;

import java.util.List;

public interface SourceResolver {
    List<String> resolve(String workspace);
}
