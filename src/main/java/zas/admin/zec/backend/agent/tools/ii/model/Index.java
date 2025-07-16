package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public sealed interface Index permits IndexTA, IndexHF, IndexRAI, IndexTH {
    String totalId();
    List<String> ids();
}
