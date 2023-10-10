import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * test cases: raw, gvm, nmg
 */
public class IDSearcher
{
    static boolean found;
    static String image_alt;
    static String imageUrl;

    /**
     * Searches for an individual field in the html with regex
     */
    static String lookupField(String html, String pattern, String field_name, StringBuilder sb)
    {
        try
        {
            Pattern pt = Pattern.compile(pattern);
            Matcher matcher = pt.matcher(html);
            matcher.find();
            String value = matcher.group(field_name);
            sb.append(field_name);
            sb.append(": ");
            if (field_name.equals("description"))
            {
                image_alt = value.split(" is ")[0];
                // System.out.println("image_alt:" + image_alt);
            }
            if (field_name.equals("imageUrl"))
            {
                imageUrl = "https://www.southampton.ac.uk" + value;
                sb.append(imageUrl);
            }
            else sb.append(value);
            sb.append(System.lineSeparator());
            found = true;
            return value;
        }
        catch(Exception e) // just ignore
        {
            return null;
        }
    }

    /**
     * Fetch html file, look up individual fields, then produce a combined output
     */
    public static String lookup(String id) throws IOException, URISyntaxException, InterruptedException
    {
        found = false;
        long start = System.currentTimeMillis();
        String url = "https://www.ecs.soton.ac.uk/people/" + id;

        HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).build();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String html = response.body();
        // System.out.println(html);
        Map<String, List<String>> headers = response.headers().map();
        String lastModified = headers.getOrDefault("last-modified", List.of("Unknown")).get(0);

        String person_pattern = "\"Person\",\\R[\\h]+";
        String name_pattern = person_pattern + "[\\s\\S]*\"name\": \"(?<name>.*)\"";
        String description_pattern = person_pattern + "[\\s\\S]*\"description\": \"(?<description>.*)\"";
        String url_pattern = person_pattern + "((?!image)[\\s\\S])*\"url\": \"(?<url>.*)\"";
        String email_pattern = person_pattern + "[\\s\\S]*\"email\": \"(?<email>.*)\"";
        String jobTitle_pattern = person_pattern + "[\\s\\S]*\"jobTitle\": \"(?<jobTitle>.*)\"";
        String addressStart_pattern = person_pattern + "[\\s\\S]*\"address\": \\{";
        String addressType_pattern = addressStart_pattern + "((?!image)[\\s\\S])*\"@type\": \"(?<addressType>.*)\"";
        String streetAddress_pattern = addressStart_pattern + "[\\s\\S]*\"streetAddress\": \"(?<streetAddress>.*)\"";
        String addressLocality_pattern = addressStart_pattern + "[\\s\\S]*\"addressLocality\": \"(?<addressLocality>.*)\"";
        String postalCode_pattern = addressStart_pattern + "[\\s\\S]*\"postalCode\": \"(?<postalCode>.*)\"";
        String addressCountry_pattern = addressStart_pattern + "[\\s\\S]*\"addressCountry\": \"(?<addressCountry>.*)\"";

        image_alt = null;

        StringBuilder sb = new StringBuilder();
        lookupField(html, name_pattern, "name", sb);
        lookupField(html, url_pattern, "url", sb);
        lookupField(html, description_pattern, "description", sb);
        lookupField(html, email_pattern, "email", sb);
        lookupField(html, jobTitle_pattern, "jobTitle", sb);
        lookupField(html, addressType_pattern, "addressType", sb);
        lookupField(html, streetAddress_pattern, "streetAddress", sb);
        lookupField(html, addressLocality_pattern, "addressLocality", sb);
        lookupField(html, postalCode_pattern, "postalCode", sb);
        lookupField(html, addressCountry_pattern, "addressCountry", sb);

        String imageUrl_pattern = "alt=\"" + image_alt + "\" src=\"(?<imageUrl>((?!\")(.))*)\"";
        lookupField(html, imageUrl_pattern, "imageUrl", sb);

        if (!found)
        {
            sb.append("no information found");
            sb.append(System.lineSeparator());
            sb.append("please check your input and try again");
            sb.append(System.lineSeparator());
        }
        sb.append("source information last modified: ");
        sb.append(lastModified);
        sb.append(System.lineSeparator());
        sb.append("search time: ");
        sb.append((System.currentTimeMillis() - start));
        sb.append("ms. ");
        return sb.toString();
    }

    public static void main(String[] args)
    {
        EventQueue.invokeLater(() ->
        {
            new InfoFrame().setVisible(true);
        });
    }
}

/**
 * GUI Interface
 */
class InfoFrame extends JFrame
{
    JTextArea infoText;
    JTextField idField;
    JLabel idLabel;
    JLabel infoLabel;
    JButton searchButton;
    JButton clearButton;
    JButton copyButton;
    JButton showImage;
    ImageComponent imageComponent;
    JPanel panel;
    JLabel imageLabel;

    class ImageComponent extends JComponent
    {
        int DEFAULT_WIDTH = 500;
        int DEFAULT_HEIGHT = 300;
        int width;
        int height;
        Image image;

        public void setURL(String url)
        {
            if (url == null)
            {
                image = null;
                width = 0;
                height = 0;
                return;
            }
            try
            {
                image = new ImageIcon(new URL(url)).getImage();
                double ratio1 = DEFAULT_HEIGHT / ((double) image.getHeight(null));
                double ratio2 = DEFAULT_HEIGHT / ((double) image.getHeight(null));
                double ratio = Math.min(ratio1, ratio2);
                width = (int) (image.getWidth(null) * ratio);
                height = (int)(image.getHeight(null) * ratio);
                image = image.getScaledInstance(width, height, Image.SCALE_DEFAULT);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void paintComponent(Graphics g)
        {
            if (image == null) return;
            int w = (int) ((DEFAULT_WIDTH + 25 - width) / 2.0);
            int h =(int) ((DEFAULT_HEIGHT + 25 - height) / 2.0);
            g.drawImage(image, w <= 12 ? 12 : w, h <= 12 ? 12 : h, null);
        }

        public Dimension getPreferredSize() { return new Dimension(DEFAULT_WIDTH + 25, DEFAULT_HEIGHT + 25); }
    }
    public InfoFrame()
    {
        infoText = new JTextArea(20, 40);
        idLabel = new JLabel("Lookup ID: ");
        idField = new JTextField(40);
        infoLabel = new JLabel("Information Found: ");
        searchButton = new JButton("Search");
        showImage = new JButton("Show Image");
        showImage.setEnabled(false);
        imageLabel = new JLabel("Image Preview: ");
        imageComponent = new ImageComponent();
        showImage.addActionListener(event -> repaint());
        imageComponent.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), BorderFactory.createBevelBorder(BevelBorder.LOWERED)));
        searchButton.addActionListener(event ->
        {
            try
            {
                infoText.setText("searching in progress...");
                IDSearcher.imageUrl = null;
                showImage.setEnabled(false);
                imageComponent.setURL(null);
                repaint();
                infoText.setText(IDSearcher.lookup(idField.getText()));
                imageComponent.setURL(IDSearcher.imageUrl);
                if (IDSearcher.imageUrl != null) showImage.setEnabled(true);
            }
            catch (Exception e)
            {
                infoText.setText("search failed.");
                JOptionPane.showMessageDialog(this, "Either no information found or search failed." + System.lineSeparator() +  "Please check your input and internet connection.", "Search Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        clearButton = new JButton("Clear");
        clearButton.addActionListener(event ->
        {
            infoText.setText("");
        });

        copyButton = new JButton("Copy");
        copyButton.addActionListener(event ->
        {
            StringSelection stringSelection = new StringSelection(infoText.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });

        panel = new JPanel(new GridBagLayout());
        panel.add(idLabel, new GBC(0, 0, 1, 1).setAnchor(GBC.EAST));
        panel.add(idField, new GBC(1, 0, 1, 1));
        panel.add(searchButton, new GBC(2, 0, 1, 1).setAnchor(GBC.WEST));
        panel.add(infoLabel, new GBC(0, 1, 1, 1).setAnchor(GBC.EAST));
        panel.add(new JScrollPane(infoText), new GBC(1, 1, 1, 3));
        panel.add(copyButton, new GBC(2, 1, 1, 1).setAnchor(GBC.WEST));
        panel.add(clearButton, new GBC(2, 2, 1, 1).setAnchor(GBC.WEST));
        panel.add(showImage, new GBC(2, 4, 1, 1).setAnchor(GBC.WEST));
        GBC gbc = new GBC(1, 4, 1, 1);
        gbc.fill = GBC.BOTH;
        panel.add(imageComponent, gbc);
        panel.add(imageLabel, new GBC(0, 4, 1, 1).setAnchor(GBC.EAST));
        int margin = 20;
        panel.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
        add(new JScrollPane(panel));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("University of Southampton ID Lookup");
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * GridBagLayout helper class
     */
    class GBC extends GridBagConstraints
    {
        public GBC(int gridx, int gridy, int gridwidth, int gridheight)
        {
            super();
            this.gridx = gridx;
            this.gridy = gridy;
            this.gridwidth = gridwidth;
            this.gridheight = gridheight;
        }

        public GBC setAnchor(int anchor)
        {
            this.anchor = anchor;
            return this;
        }
    }
}