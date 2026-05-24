package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.SaveCategoriesUseCase
import com.moneylytics.api.domain.Category
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class CategoryDataInitializer(
    private val saveCategoriesUseCase: SaveCategoriesUseCase,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        saveCategoriesUseCase.saveCategories(DEFAULT_CATEGORIES)
    }

    companion object {
        val DEFAULT_CATEGORIES =
            listOf(
                Category("Allgemeiner Bedarf", "femdisc"),
                Category("Allgemeiner Bedarf", "Fielmann"),
                Category("Allgemeiner Bedarf", "Friseur"),
                Category("Allgemeiner Bedarf", "Sonstiges"),
                Category("Auswärts Essen/Bestellen", "Burger King"),
                Category("Auswärts Essen/Bestellen", "Coffee Fellows"),
                Category("Auswärts Essen/Bestellen", "Lieferando"),
                Category("Auswärts Essen/Bestellen", "McDonalds"),
                Category("Auswärts Essen/Bestellen", "Restaurant"),
                Category("Auswärts Essen/Bestellen", "Sattgrün"),
                Category("Auswärts Essen/Bestellen", "Sonstiges"),
                Category("Auswärts Essen/Bestellen", "Starbucks"),
                Category("Auswärts Essen/Bestellen", "Subway"),
                Category("Auto", "ADAC"),
                Category("Auto", "Benzin"),
                Category("Auto", "Steuer"),
                Category("Auto", "Versicherung"),
                Category("Auto", "Waschen"),
                Category("Bauspar", "Alte Leipziger"),
                Category("Bauspar", "Targo Bank"),
                Category("Einkäufe", "ALDI"),
                Category("Einkäufe", "Amazon"),
                Category("Einkäufe", "Apotheke"),
                Category("Einkäufe", "Bäckerei"),
                Category("Einkäufe", "dm"),
                Category("Einkäufe", "Edeka"),
                Category("Einkäufe", "Flink"),
                Category("Einkäufe", "Kaufland"),
                Category("Einkäufe", "Lidl"),
                Category("Einkäufe", "Lieferando"),
                Category("Einkäufe", "Rewe"),
                Category("Einkäufe", "Sonstiges"),
                Category("Einnahmen", "Gehalt"),
                Category("Einnahmen", "Zinsen"),
                Category("Einzahlung", "Regulär"),
                Category("Einzahlung", "Sonstiges"),
                Category("Entertainment", "Amazon Prime"),
                Category("Entertainment", "Netflix"),
                Category("Entertainment", "Playstation"),
                Category("Entertainment", "Spotify"),
                Category("Freizeit", "Sport"),
                Category("Freizeit", "Streaming"),
                Category("Freizeit", "Sonstiges"),
                Category("GEZ", "Rundfunkbeitrag"),
                Category("Gesundheit", "Apotheke"),
                Category("Gesundheit", "Arzt"),
                Category("Hunde", "Fressnapf"),
                Category("Hunde", "Friseur"),
                Category("Hunde", "Futterhaus"),
                Category("Hunde", "Steuer"),
                Category("Hunde", "Tierarzt / Kurse"),
                Category("Hunde", "Versicherung"),
                Category("Kontoführung", "MLP"),
                Category("Kredit", "Targo Bank"),
                Category("Lebensmittel", "Restaurant"),
                Category("Lebensmittel", "Supermarkt"),
                Category("Miete", "Sahle"),
                Category("Mietnebenkosten", "Guthaben"),
                Category("Reisen", "Sonstiges"),
                Category("Shopping", "Elektronik"),
                Category("Shopping", "Kleidung"),
                Category("Shopping", "Sonstiges"),
                Category("Sparen", "Einzahlung"),
                Category("Sparen", "Entnahme"),
                Category("Sport", "Sonstiges"),
                Category("Sport", "Tanzfestivals"),
                Category("Sport", "Tanzschule"),
                Category("Strom", "Stadtwerke Münster"),
                Category("Telekommunikation", "Handy"),
                Category("Telekommunikation", "Internet"),
                Category("Tools", "1password"),
                Category("Tools", "Picture This"),
                Category("Tools", "Sweepy"),
                Category("Transport", "ÖPNV"),
                Category("Transport", "Tankstelle"),
                Category("Unterhaltung", "Amazon Prime"),
                Category("Unterhaltung", "Netflix"),
                Category("Unterhaltung", "Playstation"),
                Category("Unterhaltung", "Spotify"),
                Category("Versicherung", "Haftpflicht"),
                Category("Versicherung", "Hausrat"),
                Category("Versicherung", "Jahresbeitrag"),
                Category("Versicherung", "Rechtschutz"),
                Category("Wohnen", "Internet"),
                Category("Wohnen", "Miete"),
                Category("Wohnen", "Sonstiges"),
            )
    }
}
