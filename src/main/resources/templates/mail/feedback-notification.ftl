<#-- Notification envoyée aux modérateurs lors d'un feedback négatif. -->
<html>
<body style="font-family: Arial, sans-serif; font-size: 14px; color: #222;">
    <h2>Nouveau feedback négatif reçu</h2>
    <table cellpadding="6" cellspacing="0" style="border-collapse: collapse;">
        <tr><td><b>Type</b></td><td>${feedbackKind}</td></tr>
        <tr><td><b>Catégorie</b></td><td>${category!"non renseignée"}</td></tr>
        <tr><td><b>Utilisateur</b></td><td>${userUuid}</td></tr>
        <tr><td><b>Conversation</b></td><td>${conversationUuid}</td></tr>
        <tr><td><b>Message</b></td><td>${messageUuid}</td></tr>
        <#if documentId??>
        <tr><td><b>Document</b></td><td>${documentId}</td></tr>
        </#if>
        <tr><td><b>Commentaire</b></td><td>${comment!"—"}</td></tr>
        <tr><td><b>Question</b></td><td>${question!"—"}</td></tr>
        <tr><td><b>Réponse</b></td><td>${answer!"—"}</td></tr>
        <tr><td><b>Date</b></td><td>${timestamp}</td></tr>
    </table>
    <p><a href="${appBaseUrl}">Ouvrir l'application</a></p>
</body>
</html>
