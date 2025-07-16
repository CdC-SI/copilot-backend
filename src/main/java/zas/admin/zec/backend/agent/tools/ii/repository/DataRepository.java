package zas.admin.zec.backend.agent.tools.ii.repository;

import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;

import java.util.List;
import java.util.Map;

public interface DataRepository {
    Map<String, String> loadLabels();
    Map<String, List<DataPoint>> loadTables();
}
