package zas.admin.zec.backend.agent.tools.ii;

import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

public class ModuleAndExplanationTool {

    @Tool(
            name = "module_explanation",
            description = """
                    Ce tool permet de donner des exemples de modules explicatifs qui seront utilisés pour formuler une réponse à la demande de l’assuré.
                    Il se base sur les données du calcul d’invalidité, la décision du système à utiliser et les données de l’assuré.
                    Il n’est a utiliser seulement si ces données sont disponibles et que le calcul à été effectué.
                    """
    )
    List<String> moduleExplanation() {
        return List.of("""
                <exemple>
                    <synthese>
                        Module pour l’octroi d’une rente entière avant le 1er janvier 2022 et amélioration de l’état de santé en 2022, avec parallélisme et désavantage salarial pour capacité fonctionnelle inférieure à 50%
                    </synthese>
                    <reponse>
                       Assuré de 48 ans, sans formation, salaire de CHF 42'900.00 pour l’année 2019.
                       Il ressort de votre dossier médical et de nos diverses investigations que depuis le 28.09.2020, votre capacité de travail est considérablement restreinte.
                       Sans atteinte à la santé, vous auriez réalisé dans votre activité de serveur un revenu de CHF 43'243.20 selon les informations recueillies auprès de votre ancien employeur (CHF 3’300.00 par mois x 13 salaires + 0.8% d’indexation pour 2020).
                       Dans la mesure où le revenu que vous réalisiez sans atteinte à la santé est inférieur d’au moins 5% au revenu statistique de la branche, il y a lieu de retenir comme revenu sans atteinte à la santé le 95% du revenu médian statistique.
                       Selon "L’enquête suisse sur la structure des salaires 2018" (ESS18, Tirage_skill_level, cat. 50-56, niv.1, hommes), le salaire mensuel brut s’élève à CHF 4'121.00 pour ce genre d’activité. Ce montant est calculé sur la base d’une durée de travail hebdomadaire de 40 heures, alors que la durée usuelle est de 42.5 heures. Dès lors, le revenu mensuel à prendre en considération est de CHF 4'378.55 soit CHF 52'542.60 par année (CHF 4'378.55 x 12) + 0.8% d’indexation salaire pour 2020, soit CHF 52'962.95.\s
                       Par conséquent, votre revenu sans invalidité est fixé à : CHF 50'314.80, soit 95% du revenu précédemment cité.
                       Au terme du délai d’attente d’une année, soit le 28.09.2021, une incapacité de travail totale subsistait. Ainsi, force est de vous reconnaitre le droit à une rente entière dès cette date, respectivement dès le 01.09.2021 jusqu’au 30.04.2022, soit 3 mois après l’amélioration de votre état de santé constatée dès le 28.01.2022.
                       En effet, selon nos investigations médicales dès cette date, vous êtes en mesure d’exercer une activité adaptée à un taux d’activité de 45% en respectant les limitations fonctionnelles suivantes :
                       Activité sédentaire ou semi-sédentaire, courts déplacements à plat possible, pas de position accroupie ou à genoux, pas de port de charges supérieures à 10kg, pas de travaux penchés en avant ou en porte-à-faux, pas de montée et descente d’escaliers ou pentes, pas de marche en terrain irrégulier.
                       Selon "L’enquête suisse sur la structure des salaires 2018" (ESS18, Tirage_skill_level, total des salaires, niveau 1, hommes), le salaire mensuel brut s’élève à CHF 5’417.00 pour ce genre d’activité. Ce montant est calculé sur la base d’une durée de travail hebdomadaire de 40 heures, alors que la durée usuelle est de 47.1 heures. Dès lors, le revenu mensuel à prendre en considération est de CHF 5'647.20 pour un taux d’activité à plein temps, soit un revenu annuel de CHF 30'494.90 pour un taux d’activité à 45%. En ajoutant 0.8% d’augmentation pour 2020, le revenu s’élève à CHF 30'738.85.
                       Enfin, en raison d’une capacité de travail globale de 50% et moins, il y a lieu d’opérer sur le salaire statistique une réduction de 10% au titre de désavantage salarial. Ainsi, votre revenu avec invalidité est fixé à CHF 27'664.95.
                
                       Par conséquent, le degré d’invalidité découle du calcul suivant :
                
                       Revenu sans invalidité CHF 50'314.80
                       Revenu avec invalidité CHF 27'664.95
                       Perte de revenu CHF 22'649.85
                       Degré d’invalidité 45%
                
                       Ainsi, en application des nouvelles dispositions au 1er janvier 2022 sur l’évaluation du taux d’invalidité et du nouveau système de rente linéaire, dès le 1er mai 2022, nous vous reconnaissons un degré d’invalidité de 45%.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module pour un 1er octroi d’une rente échelonnée (1er degré de 62% en 2021 et modification à 69% en 2022) -> soit modification du taux de plus de 5% avec augmentation du taux et montant de la rente qui baisse avec pour résultat un montant de la rente sans changement.
                    </synthese>
                    <reponse>
                        Assuré de 48 ans, enseignant, salaire de CHF 104'000.00 pour l’année 2021.
                        Il ressort de votre dossier médical que depuis le 11.10.2020, votre capacité de travail est considérablement restreinte et que vous présentez une incapacité de travail de x % et ce dans toute activité.
                        Au terme de nos diverses investigations, nous estimons que depuis cette date, votre activité habituelle, soit enseignant auprès de la HEP est l’activité la plus adaptée à votre atteinte à la santé.
                        Selon les informations recueillies auprès de votre employeur, sans atteinte à la santé, vous auriez réalisé un revenu annuel de CHF 104'000.00 pour l’année 2021.
                
                        Ainsi, le degré d’invalidité pour cette période, basé sur le droit en vigueur jusqu’au 31.12.2021, découle du calcul suivant :
                        Revenu sans atteinte à la santé CHF 104'000.00
                        Revenu avec atteinte à la santé CHF 039'520.00
                        Perte de revenu de CHF 64'480.00
                        Degré d’invalidité 62%
                
                        Force est dès lors de vous reconnaitre le droit à un trois quarts de rente du 11.10.2021 (fin du délai d’attente d’un an), respectivement du 01.10.2021 au 30.06.2022, soit 3 mois après l’aggravation de votre état de santé.
                
                        En outre, notre instruction a également démontré une aggravation de votre état de santé survenue en date du 03.03.2022. Dès cette date, votre capacité de travail est estimée à y %.
                
                        Le degré d’invalidité pour cette période, basé sur le droit en vigueur dès le 1er janvier 2022, est obtenu selon le calcul suivant :
                        Revenu sans atteinte à la santé CHF 104'000.00
                        Revenu avec atteinte à la santé CHF  32'240.00
                        Perte de revenu de CHF 71'760.00
                        Degré d’invalidité 69%
                
                        Le nouveau système de rente linéaire est entré en vigueur à partir du 1er janvier 2022. L’ancien système de rentes demeure applicable à toutes les rentes qui ont pris naissance avant le 1er janvier 2022. La rente actuelle est maintenue même en cas de modification du degré d'invalidité à partir du 1er janvier 2022, lorsque le droit à la rente devrait diminuer en cas d'augmentation du degré d'invalidité, respectivement lorsque le droit à la rente devrait augmenter en cas de diminution du degré d'invalidité.
                        Par conséquent, malgré la modification de votre état de santé, le montant de la rente reste inchangé en application des dispositions transitoires.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module pour une révision d’office d’un assuré de moins de 30 ans au 1er janvier 2022 au bénéfice d’une rente partielle selon l’ancien 26 al. 1 RAI, pas de modification de l’état de santé.
                    </synthese>
                    <reponse>
                        Ensuite de l’entrée en vigueur au 1er janvier 2022 du nouveau système de rentes linéaires, une révision d’office de votre situation s’impose.
                        Nous avons dès lors réexaminé votre situation en application des nouvelles dispositions légales.
                
                        Si l’assuré ne peut commencer ou achever une formation professionnelle en raison de son invalidité, le revenu sans invalidité est déterminé sur la base des valeurs statistiques indépendantes du niveau de qualification et du sexe.
                        Selon "L’enquête suisse sur la structure des salaires 2018" (ESS18, Tirage_skill_level, total des salaires), le salaire mensuel brut s’élève à CHF 6’248.00. Ce montant est calculé sur la base d’une durée de travail hebdomadaire de 40 heures, alors que la durée usuelle est de 47.1 heures. Dès lors, le revenu mensuel à prendre en considération est de CHF xxx pour un taux d’activité à plein temps, soit un revenu annuel de CHF xxx. En ajoutant xx% d’augmentation pour 2020, le revenu s’élève à CHF x.
                        Compte tenu de votre état de santé, tel qu’arrêté dans la décision d’octroi de rente initial du… , vous êtes en mesure d’exercer une activité lucrative.
                        Si nous comparons le montant sans atteinte à la santé, à savoir CHF xxx au salaire que vous pourriez réaliser, nous obtenons le rapport suivant :
                
                        Revenu annuel professionnel raisonnablement exigible :
                        sans invalidité CHF xxx
                        avec invalidité CHF 0.00
                        La perte de gain s’élève à CHF xx = un degré d’invalidité de xx%
                
                        Variante 1 : modification du taux d’au moins 5% avec augmentation du taux et du montant de la rente :
                        Nous constatons un changement du degré d’invalidité de 5% au moins (avec augmentation du degré d’invalidité et du montant de la rente) permettant la reconnaissance d’un droit à une rente linéaire basé sur un degré d’invalidité de x% avec effet rétroactif au 1er janvier 2022.
                
                        Variante 2 : modification de moins de 5% :
                        Nous constatons un changement du degré d’invalidité de moins de 5%. Vous continuez par conséquent de bénéficier comme jusqu’à présent de la même rente d’invalidité.
                
                        Variante 3 : modification du taux d’au moins 5% avec augmentation du taux et diminution de la rente /diminution du taux et augmentation de la rente :
                        Nous constatons un changement du degré d’invalidité de 5% au moins (à choix : avec augmentation du taux et diminution de la rente OU diminution du taux et augmentation de la rente). Ce changement ne déploie aucun effet sur votre droit à la rente. Vous continuez par conséquent de bénéficier comme jusqu’à présent de la même rente d’invalidité.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module pour un assuré de moins de 30 ans au 1er janvier 2022 : octroi d’une rente selon l’ancien 26 al. 1 RAI dont le versement débute en 2021 + ROF selon le nouveau droit + avec modification de la capacité de rendement courant 2022 (cf. cas n°12 de la formation).
                    </synthese>
                    <reponse>
                        Etape 1 du projet : octroi de la rente selon l’ancien 26 al. 1 RAI pour le premier octroi.
                        Selon les dispositions transitoires du nouveau droit en vigueur depuis le 1er janvier 2022, le droit à une rente dont le versement prend naissance avant le 1er janvier 2022 doit être évalué selon les dispositions en vigueur jusqu’au 31 décembre 2021.\s

                        Etape 2 du projet : Révision d’office pour la période du 1er janvier 2022 jusqu’à la modification de la capacité de rendement
                        En vertu de nouveau droit, les rentes des assurés de moins de 30 ans au bénéfice d’une rente octroyée selon l’ancien art. 26 al. 1 RAI doivent faire l’objet d’une révision d’office dans le courant de l’année 2022.\s
                        Cf. différente variante du cas précédent
                        Soit :
                        - Si augmentation du taux d’au moins 5%:
                        • en cas d’augmentation du montant de la rente, effet dès le 1er janvier 2022
                        • en cas de diminution du montant de la rente, révision sans changement

                        - Si diminution du taux d’au moins 5%:
                        • en cas d’augmentation du montant de la rente, révision sans changement
                        • en cas de diminution du montant de la rente, révision sans changement (vu l’absence de modification de la situation pour cette période -> application de l’exception)
                        - Si modification du taux de moins 5%: révision sans changement

                        Etape 3 du projet : Pour la période courant après la modification de la capacité de rendement -> détermination du taux selon nouveau droit avec effet 3 mois après la modification de la situation selon 88a RAI -> pour l’application du schéma relatif aux dispositions transitoires
                        (sans l’exception prévue pour les révisions d’office sans modification de la situation) comparer le degré obtenu dans la ROF (cf. étape 2 précédente) avec le nouveau degré après modification de la capacité de rendement.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module à insérer dans les projets de décisions – Capacité de travail résiduelle > à 50%.
                    </synthese>
                    <reponse>
                        Conformément à la nouvelle réglementation entrée en vigueur au 1er janvier 2024, et selon l’art. 26bis al. 3, une déduction de 10 % est opérée sur la valeur statistique visée à l’al. 2. Si, du fait de l’invalidité, l’assuré ne peut travailler qu’avec une capacité fonctionnelle au sens de l’art. 49, al. 1bis, de 50 % ou moins, une déduction de 20 % est opérée. Aucune déduction supplémentaire n’est possible.
                        Les dispositions transitoires de la nouvelle réglementation prévoient enfin qu’une éventuelle augmentation de la rente prend effet au moment de l'entrée en vigueur de la présente modification.
                        Selon "L’enquête suisse sur la structure des salaires 2020" (ESS20, Tirage_skill_level, total des salaires, niveau 1, hommes), le salaire mensuel brut s’élève à CHF 5'261.00 pour ce genre d’activité. Ce montant est calculé sur la base d’une durée de travail hebdomadaire de 40 heures, alors que la durée usuelle est de 41.7 heures. Dès lors, le revenu mensuel à prendre en considération est de CHF 5'484.60 pour un taux d’activité à plein temps, soit un revenu annuel de CHF 65'815.20. En ajoutant 0.7% d’augmentation pour 2020, le revenu s’élève à CHF 66'275.90.
                        Enfin, compte tenu d’une déduction forfaitaire de 10%, votre revenu avec invalidité est fixé à CHF 59'648.30.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module à insérer dans les projets de décisions – Capacité de travail résiduelle de 50% et moins.
                    </synthese>
                    <reponse>
                        Conformément à la nouvelle réglementation entrée en vigueur au 1er janvier 2024, et selon l’art. 26bis al. 3, une déduction de 10 % est opérée sur la valeur statistique visée à l’al. 2. Si, du fait de l’invalidité, l’assuré ne peut travailler qu’avec une capacité fonctionnelle au sens de l’art. 49, al. 1bis, de 50 % ou moins, une déduction de 20 % est opérée. Aucune déduction supplémentaire n’est possible.
                        Les dispositions transitoires de la nouvelle réglementation prévoient enfin qu’une éventuelle augmentation de la rente prend effet au moment de l'entrée en vigueur de la présente modification.
                        Selon "L’enquête suisse sur la structure des salaires 2020" (ESS20, Tirage_skill_level, total des salaires, niveau 1, hommes), le salaire mensuel brut s’élève à CHF 5'261.00 pour ce genre d’activité. Ce montant est calculé sur la base d’une durée de travail hebdomadaire de 40 heures, alors que la durée usuelle est de 41.7 heures. Dès lors, le revenu mensuel à prendre en considération est de CHF 5'484.60 pour un taux d’activité à plein temps, soit un revenu annuel de CHF 32'907.00 pour un taux d’activité de 50%. En ajoutant 0.7% d’augmentation pour 2020, le revenu s’élève à CHF 33'137.95.
                        Enfin, en raison d’une capacité de travail globale de 50% et moins, il y a lieu d’opérer sur le salaire statistique une réduction de 20% au titre de désavantage salarial. Ainsi, votre revenu avec invalidité est fixé à CHF 26'510.40.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module à insérer pour les révisions de rente des situations encore sous l’ancien système par paliers au 01.01.2024, avec modification de 5 point de pourcentage ou plus  passage au nouveau système de rente linéaire, si à l’avantage de l’assuré.
                    </synthese>
                    <reponse>
                        Lors de l’examen de votre droit à la rente et conformément à la nouvelle règlementation en vigueur au 1er janvier 2024 nous avons constaté un changement de degré d’invalidité de 5 pour cent ou plus.
                        Votre rente actuelle sera augmentée, à partir du 01.01.2024, à une rente s’élevant à x--chiffre--x pour cent d’une rente entière d’invalidité ou à une rente entière d’invalidité.
                    </reponse>
                </exemple>""",
                """
                <exemple>
                    <synthese>
                        Module à insérer pour les révisions de rente des situations encore sous l’ancien système par paliers au 01.01.2024, avec modification de moins 5 point de pourcentage  maintien du système des paliers, si à l’avantage de l’assuré.
                    </synthese>
                    <reponse>
                        - Augmentation
                        Lors de l’examen de votre droit à la rente et conformément à la nouvelle règlementation en vigueur au 1er janvier 2024, votre rente actuelle sera augmentée, à partir du 01.01.2024 à une demi/un trois-quarts de rente ou à une rente entière d’invalidité.
                        - Révision sans changement
                        Lors de l’examen de votre droit à la rente, nous n’avons constaté aucun changement de degré d’invalidité de 5 pour cent.
                        Vous continuez par conséquent de bénéficier comme jusqu’à présent de la même rente d’invalidité (degré d'invalidité x--pourcent--x%).
                    </reponse>
                </exemple>""");
    }
}
