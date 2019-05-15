package com.codetriage.scraper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Our scraper class
 */
public class App {

    /**
     * The main method of our class, which will also house the scraping
     * functionality.
     */
    public static void main(String[] args) {
        String OUTPUT_CSV_FILE = "./amo_data.csv";

        try (
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT_CSV_FILE));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader("itemId", "family", "scientificName", "fileName", "imageThumbnailUrl"));
        ) {
            /**
             * Here we create a document object,
             * The we use JSoup to fetch the website.
             */
            Document doc = Jsoup.connect("https://amo.ala.org.au/main.php").timeout(10000).validateTLSCertificates(false).get();

            /**
             * With the document fetched,
             * we use JSoup???s title() method to fetch the title
             */
            System.out.printf("\nWebsite Title: %s\n\n", doc.title());


            // Get the list of repositories
            String giAlbumSelector = ".giAlbumCell div";
            Elements albums = doc.select(giAlbumSelector);
            int count = 0;

            /**
             * Iterate over album divs and then scrape each album page for species pages
             */
            for (Element album : albums) {
                String albumTitle = album.select("img").attr("alt");
                String albumUrl = album.select("a").attr("href");

                if (StringUtils.isNotEmpty(albumTitle) && StringUtils.isNotEmpty(albumUrl)) {
                    System.out.println(++count + ". " + albumTitle + " - " + albumUrl);

                    Document speciesDoc = Jsoup.connect("https://amo.ala.org.au/"
                            + albumUrl).timeout(10000).validateTLSCertificates(false).get();
                    Elements speciesPages = speciesDoc.select(giAlbumSelector);

                    for (Element speciesPage : speciesPages) {
                        String speciesTitle = speciesPage.select("img").attr("alt");
                        String speciesUrl = speciesPage.select("a").attr("href");
                        String speciesId = (speciesUrl.length() > 20) ? StringUtils.remove(speciesUrl.substring(15, 20), "_") : "";

                        if (StringUtils.isNotEmpty(speciesTitle) && StringUtils.isNotEmpty(speciesUrl) && StringUtils.isNotEmpty(speciesId)) {
                            System.out.println("  " + ++count + ". " + speciesTitle + " - " + speciesUrl);

                            Document specimenDoc = Jsoup.connect("https://amo.ala.org.au/"
                                    + speciesUrl).timeout(10000).validateTLSCertificates(false).get();
                            Elements specimenItems = specimenDoc.select(".giItemCell");

                            if (specimenItems.size() > 0) {
                                for (Element specimenItem : specimenItems) {
                                    String specimenDescription = specimenItem.select(".giDescription2").html();
                                    String specimenImgUrl = specimenItem.select("img").attr("src");
                                    String specimenImgFilename = specimenItem.select("img").attr("alt");
                                    String itemId = (specimenImgUrl.length() > 50) ? StringUtils.remove(specimenImgUrl.substring(45, 50), "_") : "";
                                    System.out.println("    " + ++count + ". " + itemId + " | " + specimenImgFilename + " | " + specimenImgUrl +" | " + specimenDescription );
                                    //csvPrinter.printRecord("1", "Sundar Pichai ♥", "CEO", "Google");
                                }
                            } else {
                                // withHeader("itemId", "family", "scientificName", "fileName", "imageThumbnailUrl")
                                csvPrinter.printRecord(speciesId, "Sundar Pichai ♥", "CEO", "Google");
                            }
                        }
                    }
                }
            }

            /**
             * Incase of any IO errors, we want the messages
             * written to the console
             */
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
