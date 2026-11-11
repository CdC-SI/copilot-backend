<#ftl output_format="HTML">
<#-- Rapport hebdomadaire des feedbacks envoyé aux destinataires configurés. -->
<html>
<body style="font-family: Arial, sans-serif; font-size: 14px; color: #222;">
    <h2>Rapport hebdomadaire des feedbacks</h2>
    <p>Période : ${periodStart} → ${periodEnd}</p>

    <h3>Résumé</h3>
    <table cellpadding="6" cellspacing="0" style="border-collapse: collapse;">
        <tr><td><b>Total reçus</b></td><td>${total}</td></tr>
        <tr><td><b>Traités</b></td><td>${treated}</td></tr>
        <tr><td><b>Obsolètes</b></td><td>${obsolete}</td></tr>
        <tr><td><b>Toujours en attente (NEW)</b></td><td>${stillNew}</td></tr>
    </table>

    <h3>Répartition par catégorie</h3>
    <table cellpadding="6" cellspacing="0" style="border-collapse: collapse;">
        <#list byCategory as row>
        <tr><td>${row.category}</td><td>${row.count}</td></tr>
        </#list>
    </table>

    <h3>Feedbacks toujours en attente</h3>
    <#if pendingItems?size == 0>
    <p>Aucun feedback en attente 🎉</p>
    <#else>
    <table cellpadding="6" cellspacing="0" style="border-collapse: collapse; border: 1px solid #ccc;">
        <tr><th>Date</th><th>Type</th><th>Catégorie</th><th>Question</th></tr>
        <#list pendingItems as item>
        <tr>
            <td>${item.timestamp}</td>
            <td>${item.kind}</td>
            <td>${item.category!"—"}</td>
            <td>${item.question!"—"}</td>
        </tr>
        </#list>
    </table>
    </#if>

    <p><a href="${appBaseUrl}">Ouvrir l'application</a></p>
</body>
</html>
