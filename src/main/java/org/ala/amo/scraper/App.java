/*
 * Copyright (C) 2019 Atlas of Living Australia
 * All Rights Reserved.
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package org.ala.amo.scraper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    public static final String AMO_BASE_URL = "https://amo.ala.org.au/";
    public static final String SEP = " | ";
    public static int count = 0;  // counter - needs to be global so recursive function doesn't reset it

    /**
     * The main method of our class, which will also house the scraping
     * functionality.
     */
    public static void main(String[] args) {
        String OUTPUT_CSV_FILE = "./amo_data.csv";
        App instance = new App();

        try (
                // setup the CSV writer with header row
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT_CSV_FILE));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader("itemId", "family", "scientificName", "fileName", "imageThumbnailUrl", "description"));
        ) {

            Document doc = Jsoup.connect(AMO_BASE_URL + "main.php").timeout(10000).validateTLSCertificates(false).get();
            System.out.printf("\nWebsite Title: %s\n\n", doc.title());
            instance.processAlbumDoc(doc, csvPrinter, "", ""); // recursive

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Recursive method to process a JSoup Document
     *
     * TODO: navigate the pagination for album pages
     *
     * @param doc
     * @param csvPrinter
     * @param parent
     * @param title
     * @throws IOException
     */
    private void processAlbumDoc(Document doc, CSVPrinter csvPrinter, String parent, String title) throws IOException {
        // one of the following selector will be populated, depending on the page type (album or items/photos)
        Elements albums = doc.select(".giAlbumCell div"); // album page
        Elements items = doc.select(".giItemCell");       // items page
        Elements nextLink = doc.select(".next-and-last .next"); // next page

        if (albums.size() > 0) {
            // we're on a page with sub-albums (e.g. family or subfamily) so need to go one level deeper
            for (Element album : albums) {
                String albumTitle = album.select("img").attr("alt");
                String albumThumbnailUrl = album.select("img").attr("src");
                String albumUrl = album.select("a").attr("href");
                // extract the album ID from the item-page URL using substring
                String albumId = (albumUrl.length() > 20) ? StringUtils.remove(albumUrl.substring(15, 20), "_") : "";

                if (StringUtils.isNotEmpty(albumTitle) && StringUtils.isNotEmpty(albumUrl)) {
                    System.out.println( count++ + ". " + albumTitle + SEP + albumUrl + SEP + albumId + SEP + title);
                    Document subAlbumDoc = Jsoup.connect(AMO_BASE_URL + albumUrl).timeout(10000).validateTLSCertificates(false).get();
                    String pageTitle = subAlbumDoc.title();

                    if (!StringUtils.contains(pageTitle, "Australian Moths Online")) {
                        processAlbumDoc(subAlbumDoc, csvPrinter, parent + "|" + albumTitle, pageTitle);
                    } else {
                        System.out.println("Requested page (" + AMO_BASE_URL + albumUrl + ") has redirected to AMO home");
                        csvPrinter.printRecord(albumId, parent, albumTitle, "missing", albumThumbnailUrl, "missing");
                    }
                } else {
                    //System.out.println("No album link or title found - " + albumTitle + SEP + albumUrl);
                }
            }

            // check if there are more pages of sub-albums in this album (pagination links)
            if (nextLink.size() > 0) {
                String nextPageLink = nextLink.get(0).attr("href");
                System.out.println(" > next page found " + nextPageLink);
                Document subAlbumDoc = Jsoup.connect(AMO_BASE_URL + nextPageLink).timeout(10000).validateTLSCertificates(false).get();
                String nextPageTitle = subAlbumDoc.title();

                if (!StringUtils.contains(nextPageTitle, "Australian Moths Online")) {
                    processAlbumDoc(subAlbumDoc, csvPrinter, parent, nextPageTitle);
                } else {
                    System.out.println("Requested (next) page (" + AMO_BASE_URL + nextPageLink + ") has redirected to AMO home");
                }
            }
        } else if (items.size() > 0) {
            // we're on a page with album items (photos) on it

            for (Element item : items) {
                String specimenDescription = item.select(".giDescription2").html();
                String specimenImgUrl = item.select("img").attr("src");
                String specimenImgFilename = item.select("img").attr("alt");
                // extract the item ID from the item-page URL using substring
                String itemId = (specimenImgUrl.length() > 50) ? StringUtils.remove(specimenImgUrl.substring(45, 50), "_") : "";

                if (StringUtils.isNotEmpty(specimenDescription) && StringUtils.isNotEmpty(specimenImgUrl)) {
                    System.out.println("  " + count++ + ". " + parent + SEP + itemId + SEP + specimenImgFilename + SEP + specimenImgUrl + SEP + specimenDescription);
                    // withHeader("itemId", "family", "scientificName", "fileName", "imageThumbnailUrl"));
                    csvPrinter.printRecord(itemId, parent, title, specimenImgFilename, specimenImgUrl, specimenDescription);
                } else {
                    //System.out.println("No item link or title found - " + specimenImgUrl + SEP + specimenDescription);
                }
            }
        }

        csvPrinter.flush();
    }

}
