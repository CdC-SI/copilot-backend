package zas.admin.zec.backend.agent.tools.ii.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class DataTest {

    @Autowired
    private HoursService hoursService;

    @Autowired
    private IndexationService indexationService;

    @Autowired
    private Ta1LookupService ta1LookupService;

    @ParameterizedTest
    @CsvSource({
            "01-96,2010,41.64527427",
            "10-33,2023,41.25644706",
            "19-20,2005,40.93403616",
            "52, 2016, 41.83163",
            "86-88, 2018, 41.57014069",
    })
    void verifyHoursTable(String branchId, Year referenceYear, double expectedHours) {
        assertEquals(expectedHours, hoursService.weeklyHours(branchId, referenceYear));
    }

    @ParameterizedTest
    @CsvSource({
            "MALE, 05-96, 2013, 102.5036",
            "MALE, 45-47, 2016, 103.908",
            "MALE, 64-66, 2022, 108.3",
            "MALE, 86-88, 2030, 102.4255183",
            "FEMALE, 05-09/35-39, 2019, 100.00",
            "FEMALE, 10-33, 2015, 104.9462",
            "FEMALE, 55/56, 2011, 99.9867",
            "FEMALE, 90-96, 2025, 111.7477359"
    })
    void verifyIndexesTable(Gender gender, String branchId, Year referenceYear, double expectedIndex) {
        assertEquals(expectedIndex, indexationService.index(gender, branchId, referenceYear));
    }

    @ParameterizedTest
    @CsvSource({
            "MALE, 13-15, 3, 7032.0",
            "MALE, 10-33, 1, 5474.0",
            "MALE, 28, 4, 9201.0",
            "FEMALE, 35, 2, 6518.0",
            "FEMALE, 47, 3, 5365.0",
            "FEMALE, 85, 4, 8334.0"
    })
    void verifyTa1Lookup(Gender gender, String branchId, int skill, double expectedTa1) {
        assertEquals(expectedTa1, ta1LookupService.salary(branchId, skill, gender).doubleValue());
    }
}

