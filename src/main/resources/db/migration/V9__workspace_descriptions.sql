-- Descriptions et questions hypothétiques des workspaces thématiques, utilisées par RAGTool pour
-- l'inférence LLM du workspace le plus pertinent pour une question donnée.
UPDATE workspace
SET description = 'Assurance Facultative (AFAC) : assurance destinée aux personnes de nationalité '
    || 'suisse vivant à l''étranger (hors UE/AELE, sous conditions) qui souhaitent continuer à '
    || 'cotiser aux assurances sociales suisses (AVS/AI/APG) alors qu''elles ne sont plus '
    || 'obligatoirement assurées.',
    hypothetical_questions = ARRAY[
        'Comment puis-je adhérer à l''assurance facultative en vivant à l''étranger ?',
        'Quelles sont les conditions pour cotiser à l''AFAC ?',
        'Quel est le montant des cotisations de l''assurance facultative ?',
        'Puis-je continuer à cotiser à l''AVS si je quitte la Suisse ?'
    ],
    updated_at = NOW()
WHERE name = 'AFAC';

UPDATE workspace
SET description = 'Caisse Suisse de Compensation (CSC) : caisse de compensation compétente pour '
    || 'les assurés et employeurs à l''étranger, notamment pour l''affiliation, les cotisations et '
    || 'les prestations AVS/AI des personnes résidant hors de Suisse.',
    hypothetical_questions = ARRAY[
        'Comment contacter la Caisse suisse de compensation ?',
        'Qui est affilié auprès de la CSC ?',
        'Comment déclarer un changement d''adresse à l''étranger auprès de la CSC ?',
        'Quelles démarches effectuer auprès de la CSC pour ma rente à l''étranger ?'
    ],
    updated_at = NOW()
WHERE name = 'CSC';

UPDATE workspace
SET description = 'Caisse fédérale de compensation (EAK) : caisse de compensation compétente pour '
    || 'les employés de la Confédération et des organisations proches, pour toutes les questions '
    || 'd''affiliation, de cotisations et de prestations sociales liées à cet employeur.',
    hypothetical_questions = ARRAY[
        'Comment fonctionne la caisse fédérale de compensation EAK ?',
        'Je travaille pour la Confédération, à quelle caisse de compensation suis-je affilié ?',
        'Comment obtenir une attestation de cotisations auprès de l''EAK ?',
        'Quelles prestations l''EAK gère-t-elle pour les employés fédéraux ?'
    ],
    updated_at = NOW()
WHERE name = 'EAK';

UPDATE workspace
SET description = 'Lois et bases légales de l''assurance-invalidité (AI) suisse : LAI, RAI, LPGA '
    || 'et autres textes réglementaires régissant les prestations, procédures et conditions de '
    || 'l''assurance-invalidité.',
    hypothetical_questions = ARRAY[
        'Quelles sont les conditions légales pour obtenir une rente d''invalidité ?',
        'Que prévoit la loi sur l''assurance-invalidité concernant les mesures de réadaptation ?',
        'Quel article de la LAI régit le calcul du degré d''invalidité ?',
        'Quelles sont les bases légales des prestations de l''AI ?'
    ],
    updated_at = NOW()
WHERE name = 'LOIS_AI';

UPDATE workspace
SET description = 'Lois et bases légales de l''assurance-vieillesse et survivants (AVS) suisse : '
    || 'LAVS, LPGA et autres textes réglementaires régissant les cotisations, rentes et prestations '
    || 'de l''AVS.',
    hypothetical_questions = ARRAY[
        'Quelles sont les bases légales du calcul de la rente AVS ?',
        'Que dit la LAVS sur l''âge de la retraite ?',
        'Quel article de loi régit les cotisations AVS des indépendants ?',
        'Quelles sont les conditions légales pour une rente de veuve/veuf AVS ?'
    ],
    updated_at = NOW()
WHERE name = 'LOIS_AVS';

UPDATE workspace
SET description = 'Assurance-invalidité pour les personnes de nationalité étrangère (OAIE) et '
    || 'accords bilatéraux de sécurité sociale : coordination internationale des prestations AI '
    || 'pour les ressortissants étrangers et les situations transfrontalières.',
    hypothetical_questions = ARRAY[
        'Ai-je droit à une rente AI en tant que ressortissant étranger ?',
        'Comment fonctionnent les accords bilatéraux pour l''assurance-invalidité ?',
        'Quelles règles s''appliquent à un travailleur frontalier pour l''AI ?',
        'Quelles conventions internationales couvrent l''invalidité pour les étrangers ?'
    ],
    updated_at = NOW()
WHERE name = 'OAIE';

UPDATE workspace
SET description = 'Ressources humaines (RH) : questions internes destinées aux employés de la '
    || 'Centrale de compensation (CdC), portant sur les congés, le timbrage des heures, les '
    || 'procédures RH, le statut du personnel et les informations pratiques liées à l''emploi.',
    hypothetical_questions = ARRAY[
        'Comment poser une demande de congé ?',
        'Comment fonctionne le timbrage des heures de travail ?',
        'Quelle est la procédure pour annoncer une absence maladie ?',
        'Où trouver les informations RH pour les employés de la CdC ?'
    ],
    updated_at = NOW()
WHERE name = 'RH';
