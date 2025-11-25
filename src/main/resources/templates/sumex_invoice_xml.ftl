<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="..\xsl\generalInvoiceRequest_430.xsl" ?>
<invoice:request xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				 xmlns:invoice="http://www.forum-datenaustausch.ch/invoice"
				 xsi:schemaLocation="http://www.forum-datenaustausch.ch/invoice generalInvoiceRequest_500.xsd"
				 xmlns="http://www.forum-datenaustausch.ch/invoice" language="fr" modus="test"
				 guid="66bf64b2841d4e608c5db2c39eb07b69" validation_status="0">
	<invoice:processing>
		<invoice:transport from="${author.gln()}" to="7601003002751">
			<invoice:via via="7601003002751" sequence_id="1"/>
		</invoice:transport>
	</invoice:processing>
	<invoice:payload request_type="invoice" request_subtype="normal">
		<invoice:credit request_timestamp="1732921200" request_date="2024-11-30T00:00:00" request_id="23_45.01"/>
		<invoice:invoice request_timestamp="1755237734" request_date="${creationDate}" request_id="${metadata.invoiceNumber()}"/>
		<invoice:body role_title="${author.name()}" role="physician" place="practice">
			<invoice:prolog>
				<invoice:package name="GeneralInvoiceRequestTest" copyright="CdC" version="1"/>
				<invoice:generator name="ZAS Copilot" copyright="CdC" version="1"/>
			</invoice:prolog>
			<invoice:remark>Ceci est une remarque</invoice:remark>
			<invoice:tiers_payant allowModification="0">
				<invoice:billers>
					<invoice:biller_gln gln="${author.gln()}">
						<invoice:company>
							<invoice:companyname>${author.name()}</invoice:companyname>
							<invoice:department>Abteilung Inkasso</invoice:department>
							<invoice:postal>
								<invoice:street street_name="Billerweg" house_no="128">Billerweg 128</invoice:street>
								<invoice:zip>4414</invoice:zip>
								<invoice:city>${author.locality()}</invoice:city>
							</invoice:postal>
							<invoice:telecom>
								<invoice:phone>061 956 99 00</invoice:phone>
							</invoice:telecom>
							<invoice:online>
								<invoice:email>info@biller.ch</invoice:email>
							</invoice:online>
						</invoice:company>
					</invoice:biller_gln>
				</invoice:billers>
				<invoice:debitor gln="7601003002584">
					<invoice:company>
						<invoice:companyname>IV-Stelle Basel-Stadt</invoice:companyname>
						<invoice:postal>
							<invoice:street street_name="Kassengraben" house_no="222">Kassengraben 222</invoice:street>
							<invoice:zip>4000</invoice:zip>
							<invoice:city>Basel</invoice:city>
						</invoice:postal>
					</invoice:company>
				</invoice:debitor>
				<invoice:providers>
					<invoice:provider_gln gln="${author.gln()}" gln_location="7601003002751">
						<invoice:company>
							<invoice:companyname>${author.name()}</invoice:companyname>
							<invoice:postal>
								<invoice:street street_name="Arztgasse" house_no="17b5">Arztgasse 17b5</invoice:street>
								<invoice:zip state_code="LU">6000</invoice:zip>
								<invoice:city>${author.locality()}</invoice:city>
							</invoice:postal>
							<invoice:telecom>
								<invoice:phone>041 956 99 00</invoice:phone>
							</invoice:telecom>
							<invoice:online>
								<invoice:email>gruppenpraxis@musteraerzte.ch</invoice:email>
							</invoice:online>
						</invoice:company>
					</invoice:provider_gln>
				</invoice:providers>
				<invoice:insurance gln="7601003002584">
					<invoice:company>
						<invoice:companyname>IV-Stelle Basel-Stadt</invoice:companyname>
						<invoice:postal>
							<invoice:street street_name="Kassengraben" house_no="222">Kassengraben 222</invoice:street>
							<invoice:zip>4000</invoice:zip>
							<invoice:city>Basel</invoice:city>
						</invoice:postal>
					</invoice:company>
				</invoice:insurance>
				<invoice:patient gender="${patient.gender()?blank_to_null!'male'}" sex="${patient.gender()?blank_to_null!'male'}" birthdate="${patient.birthday()?blank_to_null!'1990-20-12'}" ssn="${patient.avsNumber()?blank_to_null!'7560665324444'}">
					<invoice:person salutation="<#if patient.gender() == 'female'>Frau<#elseif patient.gender() == 'male'>Herr<#else>male</#if>">
						<invoice:familyname>${patient.lastName()}</invoice:familyname>
						<invoice:givenname>${patient.firstName()}</invoice:givenname>
						<invoice:postal>
							<invoice:street>${patient.street()}</invoice:street>
							<invoice:zip>${patient.postalCode()}</invoice:zip>
							<invoice:city>${patient.locality()}</invoice:city>
						</invoice:postal>
						<invoice:online>
							<invoice:email>test.name@hotmail.com</invoice:email>
							<invoice:uri>www.very_cool.biz</invoice:uri>
						</invoice:online>
					</invoice:person>
				</invoice:patient>
				<invoice:guarantor>
					<invoice:person salutation="Frau">
						<invoice:familyname>Muster-Meier</invoice:familyname>
						<invoice:givenname>Petra</invoice:givenname>
						<invoice:postal>
							<invoice:street street_name="Musterstrasse" house_no="5">Musterstrasse 5</invoice:street>
							<invoice:zip state_code="LU">6001</invoice:zip>
							<invoice:city>Luzern</invoice:city>
						</invoice:postal>
						<invoice:online>
							<invoice:email>test.name@hotmail.com</invoice:email>
							<invoice:uri>www.very_cool.biz</invoice:uri>
						</invoice:online>
					</invoice:person>
				</invoice:guarantor>
				<invoice:partners>
					<invoice:partner type="referrer" gln="7634567800333">
						<invoice:person salutation="Herr" title="Dr. med.">
							<invoice:familyname>Ueberweiser</invoice:familyname>
							<invoice:givenname>Herbert</invoice:givenname>
							<invoice:postal>
								<invoice:street street_name="Referrerstrasse" house_no="11">Referrerstrasse 11</invoice:street>
								<invoice:zip state_code="AG">5000</invoice:zip>
								<invoice:city>Aarau</invoice:city>
							</invoice:postal>
							<invoice:telecom>
								<invoice:phone>061 956 99 00</invoice:phone>
							</invoice:telecom>
						</invoice:person>
					</invoice:partner>
					<invoice:partner type="employer" gln="2034567890333">
						<invoice:company>
							<invoice:companyname>Arbeitgeber AG</invoice:companyname>
							<invoice:department>R&amp;D</invoice:department>
							<invoice:postal>
								<invoice:street street_name="Arbeitsplatz" house_no="3-5">Arbeitsplatz 3-5</invoice:street>
								<invoice:zip state_code="BL">4410</invoice:zip>
								<invoice:city>Liestal</invoice:city>
							</invoice:postal>
						</invoice:company>
					</invoice:partner>
					<invoice:partner type="lead_doctor" gln="7600345600123">
						<invoice:person salutation="Frau" title="Prof. Dr. med.">
							<invoice:familyname>Musterfrau - Tester</invoice:familyname>
							<invoice:givenname>Sabine</invoice:givenname>
							<invoice:postal>
								<invoice:street street_name="Spital am Ring" house_no="16b">Spital am Ring 16b</invoice:street>
								<invoice:zip state_code="ZH">8008</invoice:zip>
								<invoice:city>Zürich</invoice:city>
							</invoice:postal>
						</invoice:person>
					</invoice:partner>
				</invoice:partners>
				<invoice:balance currency="${paymentInformation.currency()}" amount="${totalAmount?c}" amount_due="${totalAmount?c}">
					<invoice:vat vat="0.00" vat_number="CHE108791452">
						<invoice:vat_rate vat="0.00" vat_rate="0" amount="${totalAmount?c}"/>
					</invoice:vat>
				</invoice:balance>
			</invoice:tiers_payant>
			<invoice:esrQR iban="${paymentInformation.iban()}" subtype="esrQR" reference_number="${paymentInformation.reference()?blank_to_null!'000000000000000322530147912'}" payment_period="P35D" payment_reason="${paymentInformation.additionalInfo()?blank_to_null!'no reason provided'}">
				<invoice:creditor>
					<invoice:company>
						<invoice:companyname>${paymentInformation.name()}</invoice:companyname>
						<invoice:department> GmbH &amp; Co KG</invoice:department>
						<invoice:postal>
							<invoice:street>${paymentInformation.street()}</invoice:street>
							<invoice:zip>${paymentInformation.postalCode()}</invoice:zip>
							<invoice:city>${paymentInformation.locality()}</invoice:city>
						</invoice:postal>
					</invoice:company>
				</invoice:creditor>
			</invoice:esrQR>
			<invoice:law type="IVG" case_id="${patient.caseNumber()?blank_to_null!'312280'}" case_date="${patient.accidentDate()?blank_to_null!'2025-01-01'}"/>
			<invoice:treatment date_begin="${metadata.treatmentFrom()?blank_to_null!'2025-01-01'}" date_end="${metadata.treatmentTo()?blank_to_null!'2025-01-01'}" canton="GR" reason="disease" apid="patID_1456" acid="case12.005-3">
				<invoice:diagnosis type="cantonal" code="N1"/>
			</invoice:treatment>
			<invoice:services>
				<#list medicalServices as service>
					<invoice:service_ex
							record_id="${service_index + 1}"
							tariff_type="${service.tariff()?blank_to_null!'000'}"
							code="${service.code()}"
							session="1"
							quantity="${service.quantity()?blank_to_null!'1'}"
							date_begin="2025-01-01T00:00:00"
							provider_id="${author.gln()?blank_to_null!'7601003002751'}"
							responsible_id="${author.gln()?blank_to_null!'7601003002751'}"
							billing_role="both"
							medical_role="self_employed"
							body_location="none"
							unit_mt="${(service.amount() / service.quantity())?c}"
							unit_factor_mt="1"
							scale_factor_mt="1"
							amount_mt="${(service.amount() / service.quantity())?c}"
							unit_tt="0"
							unit_factor_tt="1"
							scale_factor_tt="1"
							amount_tt="0"
							amount="${service.amount()?c}"
							service_attributes="0"
							section_code="M600.01"
							name="${service.description()}"/>
				</#list>
			</invoice:services>
		</invoice:body>
	</invoice:payload>
</invoice:request>
    
