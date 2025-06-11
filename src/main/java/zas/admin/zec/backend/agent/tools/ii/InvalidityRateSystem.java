package zas.admin.zec.backend.agent.tools.ii;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zas.admin.zec.backend.agent.tools.ii.IITools.Qa;

import java.util.ArrayList;
import java.util.List;

public class InvalidityRateSystem {

    private static List<Path> getAllPaths() {
        ObjectMapper mapper = new ObjectMapper();

        String jsonTree = "[" +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\","
                +
                "        \"Art. 88bis al. 2 RAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre b point 1 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Ch. 9102 CIRAI\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre c des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 70%?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 70%?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 50%?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Ch. 9212 CIRAI\"," +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 50%?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\","
                +
                "        \"Ch. 9102 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il un changement de palier selon l'ancien système?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Ch. 9212 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il un changement de palier selon l'ancien système?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b point 1 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Ch. 9211 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Droit ouvert dans le système linéaire?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Art. 87 RAI\"," +
                "        \"Ch. 5101 CIRAI\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre c des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 70%?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 70%?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une diminution du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Ch. 9211 CIRAI\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 50%?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Ch. 9212 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Le taux d'invalidité est-il d'au-moins 50%?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du montant de la rente?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il un changement de palier selon l'ancien système?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Ch. 9212 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il un changement de palier selon l'ancien système?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b point 1 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Ch. 9211 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Art. 87 RAI\"," +
                "        \"Ch. 5101 CIRAI\"" +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente par pallier\"," +
                "      \"sources\": [" +
                "        \"Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)\","
                +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il une augmentation du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art. 88bis al. 2 RAI\"," +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Oui\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Art 17 al. 1 let. b LPGA\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Oui\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"path\": [" +
                "      {" +
                "        \"question\": \"S'agit-il d'une révision (sur demande ou d'office)?\"," +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?\","
                +
                "        \"answer\": \"Non\"" +
                "      }," +
                "      {" +
                "        \"question\": \"Y a-t-il eu une augmentation du taux depuis le 01.01.2024?\"," +
                "        \"answer\": \"Non\"" +
                "      }" +
                "    ]," +
                "    \"answer\": {" +
                "      \"decision\": \"Rente linéaire\"," +
                "      \"sources\": [" +
                "        \"Ch. 9102 CIRAI\"," +
                "        \"Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)\""
                +
                "      ]" +
                "    }" +
                "  }" +
                "]";

        List<Path> allPaths;
        try {
            allPaths = mapper.readValue(jsonTree, new TypeReference<List<Path>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing JSON", e);
        }

        return allPaths;

    }

    static String getDecision(List<Qa> info) {
        List<Path> allPaths = getAllPaths();
//        List<Path> filteredPaths;

        if (info.isEmpty()) {
            return "Plus d'informations sont nécessaires pour déterminer le système de rente. Pour commencer, S'agit-il d'une révision (sur demande ou d'office) ?";
        } else {
            List<Path> filteredPaths = new ArrayList<>();
            Path bestMatchPath = allPaths.getFirst();
            int maxMatches = 0;

            for (Path path : allPaths) {
                int currentMatches = 0;
                boolean allPathQasMatch = true;

                // Count how many QAs from info match this path
                for (Qa givenQa : info) {
                    for (Qa pathQa : path.path) {
                        if (pathQa.equals(givenQa)) {
                            currentMatches++;
                            break;
                        }
                    }
                }

                // Check if all QAs in the path are present in info
                for (Qa pathQa : path.path) {
                    boolean qaFound = false;
                    for (Qa givenQa : info) {
                        if (pathQa.equals(givenQa)) {
                            qaFound = true;
                            break;
                        }
                    }
                    if (!qaFound) {
                        allPathQasMatch = false;
                        break;
                    }
                }

                // If all path QAs match, we've found our perfect match
                if (allPathQasMatch) {
//                    bestMatchPath = path;
                    return "Décision : " + path.answer.decision;
//                    break;
                }

                // Otherwise, update bestMatchPath if this path has more matches
                if (currentMatches > maxMatches) {
                    maxMatches = currentMatches;
                    bestMatchPath = path;
                }
            }

                // Find first question from bestMatchPath that's not in info
            for (Qa pathQa : bestMatchPath.path) {
                boolean qaExists = false;
                for (Qa givenQa : info) {
                    if (pathQa.equals(givenQa)) {
                        qaExists = true;
                        break;
                    }
                }
                if (!qaExists) {
                    return "Question suivante suggérée : " + pathQa.question();  // Return the first question that's not in info
                }
            }
            return "";

            // Now bestMatchPath contains either:
            // 1. A path where all its QAs match (if we found one)
            // 2. The path with the most matching QAs (if no perfect match was found)

//             Filter paths that match all given Qa
//        List<Path> filteredPaths = allPaths.stream()
//                .filter(path -> info.stream()
//                        .allMatch(givenQa -> path.path.stream()
//                                .anyMatch(pathQa -> pathQa.question.equals(givenQa.question) &&
//                                        pathQa.answer.equals(givenQa.answer))))
//                .toList();

//            if (filteredPaths.isEmpty()) {
//                return "Erreur: Aucune décision ne correspond aux informations fournies.";
//            }

//            if (bestMatchPath.path.size() == Max) {
//                return "Décision : " + filteredPaths.get(0).answer.decision;
//            }

//            Map<String, Integer> freq = new HashMap<>();
//            for (Path p : filteredPaths) {
//                for (Qa qa : p.path) {
//                    freq.put(qa.question(), freq.getOrDefault(qa.question(), 0) + 1);
//                }
//            }

//            String mostFrequentQuestion = Collections.max(
//                    freq.entrySet(),
//                    Comparator.comparingInt(Map.Entry::getValue)).getKey();

//            return "Question suivante suggérée : " + mostFrequentQuestion;
        }
    }

    public static class Answer {
        public String decision;
        public List<String> sources;

        public Answer() {
        }

        public Answer(String decision, List<String> sources) {
            this.decision = decision;
            this.sources = sources;
        }
    }

    public static class Path {
        public List<Qa> path;
        public Answer answer;

        public Path() {
        }

        public Path(List<Qa> path, Answer answer) {
            this.path = path;
            this.answer = answer;
        }
    }
}
