package gov.nasa.gsfc.seadas.processing.general;

import com.bc.ceres.swing.selection.SelectionChangeListener;
import com.bc.ceres.swing.selection.support.ComboBoxSelectionContext;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductFilter;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.BeamFileChooser;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: Aynur Abdurazik (aabduraz)
 * Date: 8/27/13
 * Time: 3:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class SeadasFileSelector {
    private AppContext appContext;
    private ProductFilter productFilter;
    private Product extraProduct;
    private File extraFile;
    private File currentDirectory;
    private DefaultComboBoxModel productListModel;
    private DefaultComboBoxModel fileListModel;
    private JLabel productNameLabel;
    private JButton productFileChooserButton;
    private JComboBox fileNameComboBox;
    private JComboBox productNameComboBox;
    private final ProductManager.Listener productManagerListener;
    private ComboBoxSelectionContext selectionContext;
    private RegexFileFilter regexFileFilter;
    private JTextField filterRegexField;
    private JLabel filterRegexLabel;
    private boolean selectMultipleIFiles;

    private Product sampleProductForMultiIfiles;


    public SeadasFileSelector(AppContext appContext, String labelText) {
        this(appContext, labelText, false);
    }

    public SeadasFileSelector(AppContext appContext, String labelText, boolean selectMultipleIFiles) {

        this.selectMultipleIFiles = selectMultipleIFiles;
        this.appContext = appContext;

        productListModel = new DefaultComboBoxModel();
        fileListModel = new DefaultComboBoxModel();

        productNameLabel = new JLabel(labelText);
        productFileChooserButton = new JButton(new ProductFileChooserAction());

        productNameComboBox = new JComboBox(productListModel);
        fileNameComboBox = new JComboBox(fileListModel);
        fileNameComboBox.setPrototypeDisplayValue("[1] 123456789 123456789 123456789 123456789 123456789");
        fileNameComboBox.setRenderer(new ProductListCellRenderer());
        fileNameComboBox.setPreferredSize(fileNameComboBox.getPreferredSize());
        fileNameComboBox.setMinimumSize(fileNameComboBox.getPreferredSize());
        fileNameComboBox.addPopupMenuListener(new ProductPopupMenuListener());
        fileNameComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Product product = (Product) productNameComboBox.getSelectedItem();
                final File file = (File) fileNameComboBox.getSelectedItem();
                if (file != null) {
                    fileNameComboBox.setToolTipText(file.getPath());
                 } else {
                    fileNameComboBox.setToolTipText("Select an input file.");
                }
            }
        });

        selectionContext = new ComboBoxSelectionContext(fileNameComboBox);

        productManagerListener = new ProductManager.Listener() {
            @Override
            public void productAdded(ProductManager.Event event) {
                //addProduct(event.getProduct());
                fileListModel.addElement(event.getProduct().getFileLocation());
            }

            @Override
            public void productRemoved(ProductManager.Event event) {
                Product product = event.getProduct();
                if (fileListModel.getSelectedItem() == product.getFileLocation()) {
                    fileListModel.setSelectedItem(null);
                }
                fileListModel.removeElement(product.getFileLocation());
            }
        };
        regexFileFilter = new RegexFileFilter();
        sampleProductForMultiIfiles = null;

    }

    public void setEnabled(boolean enabled) {
        fileNameComboBox.setEnabled(enabled);
        productNameLabel.setEnabled(enabled);
        productFileChooserButton.setEnabled(enabled);
        filterRegexField.setEnabled(enabled);
        filterRegexLabel.setEnabled(enabled);
    }

    public SeadasFileSelector(AppContext appContext) {
        this(appContext, "Name:");
    }

    /**
     * @return the product filter, default is a filter which accepts all products
     */
    public ProductFilter getProductFilter() {
        return productFilter;
    }

    /**
     * @param productFilter the product filter
     */
    public void setProductFilter(ProductFilter productFilter) {
        this.productFilter = productFilter;
    }

    /*
      Original initProducts method, which initializes the file chooser combo box with the products opened in the SeaDAS file browser.
      It should only list file names, without associated prodcut details.
     */
    public synchronized void initProducts() {
        fileListModel.removeAllElements();
        for (Product product : appContext.getProductManager().getProducts()) {
            if (regexFileFilter.accept(product.getFileLocation())) {
                fileListModel.addElement(product.getFileLocation());
            }
        }
        final Product selectedProduct = appContext.getSelectedProduct();
        if (selectedProduct != null && regexFileFilter.accept(selectedProduct.getFileLocation())) {
            fileListModel.setSelectedItem(selectedProduct.getFileLocation());
        }
        appContext.getProductManager().addListener(productManagerListener);
    }

    private synchronized void addFiles(String fileName) {
        fileListModel.removeAllElements();
        for (Product product : appContext.getProductManager().getProducts()) {
            addProduct(product);

        }
        final Product selectedProduct = appContext.getSelectedProduct();
        if (selectedProduct != null && productFilter.accept(selectedProduct) && regexFileFilter.accept(selectedProduct.getFileLocation())) {
            //ifileTextfield.setText(selectedProduct.getName());
            setSelectedProduct(selectedProduct);
        }
        appContext.getProductManager().addListener(productManagerListener);
    }


    public Product getSelectedProduct() {
        if (sampleProductForMultiIfiles == null) {
            return (Product) productListModel.getSelectedItem();
        } else {
            return sampleProductForMultiIfiles;
        }
    }

    public Product getSelectedProductsSample() {
        return sampleProductForMultiIfiles;
    }

    public void setCurrentDirectory(File directory) {
        if (directory != null && directory.isDirectory()) {
            currentDirectory = directory;
        }
    }

    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setSelectedFile(File file) {
        if (file == null) {
            fileListModel.setSelectedItem(null);
            return;
        }
        if (regexFileFilter.accept(file)) {
            if (fileListModelContains(file)) {
                fileListModel.setSelectedItem(file);
            } else {
                fileListModel.addElement(file);
                fileListModel.setSelectedItem(file);
            }
        }
        productNameComboBox.revalidate();
        productNameComboBox.repaint();
    }

    public void setSelectedProduct(Product product) {
        if (product == null) {
            productListModel.setSelectedItem(null);
            return;
        }
        if (productListModelContains(product) && regexFileFilter.accept(product.getFileLocation())) {
            productListModel.setSelectedItem(product);
        } else {
            if (productFilter.accept(product) && regexFileFilter.accept(product.getFileLocation())) {
                productListModel.addElement(product);
                productListModel.setSelectedItem(product);
                if (extraProduct != null) {
                    productListModel.removeElement(extraProduct);
                    extraProduct.dispose();
                }

                extraProduct = product;
            }
        }
        productNameComboBox.revalidate();
        productNameComboBox.repaint();
    }

    public synchronized void releaseProducts() {
        appContext.getProductManager().removeListener(productManagerListener);
        if (extraProduct != null && getSelectedProduct() != extraProduct) {
            extraProduct.dispose();
        }
        extraProduct = null;
        productListModel.removeAllElements();
    }

    public void addFocusListener(FocusListener listener) {
        //ifileTextfield.addFocusListener(listener);
    }

    public void addSelectionChangeListener(SelectionChangeListener listener) {
        selectionContext.addSelectionChangeListener(listener);
    }

    public void removeSelectionChangeListener(SelectionChangeListener listener) {
        selectionContext.removeSelectionChangeListener(listener);
    }

    private void addProduct(Product product) {
        if (productFilter.accept(product) && regexFileFilter.accept(product.getFileLocation())) {
            productListModel.addElement(product);
        }
    }

    // UI Components

    /////////////////////////////////////

    public JComboBox getProductNameComboBox() {
        return productNameComboBox;
    }

    public JLabel getProductNameLabel() {
        return productNameLabel;
    }

    public void setProductNameLabel(JLabel jLabel) {
        this.productNameLabel = jLabel;
    }

    public JButton getProductFileChooserButton() {
        return productFileChooserButton;
    }

    private boolean productListModelContains(Product product) {
        for (int i = 0; i < productListModel.getSize(); i++) {
            if (productListModel.getElementAt(i).equals(product)) {
                return true;
            }
        }
        return false;
    }

    private boolean fileListModelContains(File file) {
        for (int i = 0; i < fileListModel.getSize(); i++) {
            if (fileListModel.getElementAt(i).equals(file)) {
                return true;
            }
        }
        return false;
    }


    public JPanel createDefaultPanel() {
        return createDefaultPanel(true);
    }


    public JPanel createDefaultPanel(boolean includeLabel) {
        JPanel mainPanel = new JPanel(new GridBagLayout());

        if (includeLabel) {
            addLabelToMainPanel(mainPanel);
        }

        mainPanel.add(getProductNameComboBox(),
                new GridBagConstraintsCustom(1, 0, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, 2));
        mainPanel.add(getProductFileChooserButton(),
                new GridBagConstraintsCustom(2, 0, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, 2));
        return mainPanel;
    }

    private void addLabelToMainPanel(JPanel jPanel) {
        jPanel.add(getProductNameLabel(),
                new GridBagConstraintsCustom(0, 0, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, 2));
    }

    private JPanel createFilterPane() {

        filterRegexField = new JTextField("123456789 ");
        filterRegexField.setPreferredSize(filterRegexField.getPreferredSize());
        filterRegexField.setMinimumSize(filterRegexField.getPreferredSize());
        filterRegexField.setMaximumSize(filterRegexField.getPreferredSize());
        filterRegexField.setText("");
        filterRegexField.setName("filterRegexField");

        filterRegexLabel = new JLabel("Filter:");
        filterRegexLabel.setPreferredSize(filterRegexLabel.getPreferredSize());
        filterRegexLabel.setMinimumSize(filterRegexLabel.getPreferredSize());
        filterRegexLabel.setMaximumSize(filterRegexLabel.getPreferredSize());
        filterRegexLabel.setToolTipText("Filter the chooser by regular expression");


        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setLayout(new FlowLayout());
        mainPanel.add(filterRegexLabel);
        mainPanel.add(filterRegexField);
        return mainPanel;

    }

    private File[] files;

    public File[] getSelectedMultiFiles() {
        return files;
    }

    private class ProductFileChooserAction extends AbstractAction {

        private String APPROVE_BUTTON_TEXT = "Select";
        private JFileChooser fileChooser;

        private ProductFileChooserAction() {
            super("...");
            fileChooser = new BeamFileChooser();
            JPanel filterPanel = createFilterPane();
            JPanel filePanel = (JPanel) fileChooser.getComponent(1);
            filePanel.add(filterPanel, BorderLayout.CENTER, 0);

            final Vector<RegexFileFilter> regexFilters = new Vector<RegexFileFilter>();

            final JTextField filterRegexField = (JTextField) filterPanel.getComponent(1);

            filterRegexField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent documentEvent) {
                    updateFileFilter();
                }

                @Override
                public void removeUpdate(DocumentEvent documentEvent) {
                    updateFileFilter();
                }

                @Override
                public void changedUpdate(DocumentEvent documentEvent) {
                    updateFileFilter();
                }
            });

            fileChooser.setMultiSelectionEnabled(selectMultipleIFiles);
            fileChooser.setDialogTitle("Select Input File");
            final Iterator<ProductReaderPlugIn> iterator = ProductIOPlugInManager.getInstance().getAllReaderPlugIns();
            while (iterator.hasNext()) {
                // todo - (mp, 2008/04/22)check if product file filter is applicable
                fileChooser.addChoosableFileFilter(iterator.next().getProductFileFilter());
            }

            // todo - (mp, 2008/04/22)check if product file filter is applicable
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.setFileFilter(fileChooser.getAcceptAllFileFilter());
            fileChooser.setFileHidingEnabled(true);
        }

        private void updateFileFilter() {
            regexFileFilter = new RegexFileFilter(filterRegexField.getText());
            //removePreviousFilters(regexFilters);
            fileChooser.resetChoosableFileFilters();
            fileChooser.addChoosableFileFilter(regexFileFilter);
            fileChooser.getUI().rescanCurrentDirectory(fileChooser);

            //regexFilters.add(regexFileFilter);
            SeadasLogger.getLogger().warning(regexFileFilter.getDescription());
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            final Window window = SwingUtilities.getWindowAncestor((JComponent) event.getSource());

            String homeDirPath = SystemUtils.getUserHomeDir().getPath();
            String openDir = appContext.getPreferences().getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR,
                    homeDirPath);
            currentDirectory = new File(openDir);
            fileChooser.setCurrentDirectory(currentDirectory);

            //fileChooser.addChoosableFileFilter(regexFileFilter);

            if (fileChooser.showDialog(window, APPROVE_BUTTON_TEXT) == JFileChooser.APPROVE_OPTION) {

                currentDirectory = fileChooser.getCurrentDirectory();
                appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR,
                        currentDirectory.getAbsolutePath());

                if (selectMultipleIFiles && fileChooser.getSelectedFiles().length > 1) {
                    handleMultipFileSelection(window);
                    return;
                }

                final File file = fileChooser.getSelectedFile();
                Product product = null;
                NetcdfFile ncFile1 = null;
                final String filePath = file.getAbsolutePath();
                try {
                    product = ProductIO.readProduct(file);
                    ncFile1 = NetcdfFile.open(filePath);

                } catch (Exception e) {
                }

                try {
                    if (product == null) {
                        if (file.canRead()) {
                            product = new Product(file.getName(), "DummyType", 10, 10);
                            product.setFileLocation(file);
                        } else {
                            throw new IOException(MessageFormat.format("File ''{0}'' could not be read.", file.getPath()));
                        }

                    }

                    if (productFilter.accept(product) && regexFileFilter.accept(file)) {
                        setSelectedProduct(product);
                    } else {
                        final String message = String.format("Product [%s] is not a valid source.",
                                product.getFileLocation().getCanonicalPath());
                        handleError(window, message);
                        SeadasLogger.getLogger().warning(" product is hidden: " + new Boolean(product.getFileLocation().isHidden()).toString());
                        product.dispose();
                    }
                } catch (Exception e) {
                    if (product != null) {
                        product.dispose();
                    }
                    handleError(window, e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void handleMultipFileSelection(Window window) {
            File[] tmpFiles = fileChooser.getSelectedFiles();
            ArrayList<File> tmpArrayList = new ArrayList<File>();
            //Product product = null;
            //Product sampleProduct = null;
            boolean firstTime = true;
            for (File file : tmpFiles) {
                try {
                    Product product = ProductIO.readProduct(file);
                    if (product == null) {
                        if (file.canRead()) {
                            product = new Product(file.getName(), "DummyType", 10, 10);
                            product.setFileLocation(file);
                            product.setDescription(file.getAbsolutePath());
                        } else {
                            throw new IOException(MessageFormat.format("File ''{0}'' could not be read.", file.getPath()));
                        }
                    } else {
                        if (firstTime) {
                            firstTime = true;
                            sampleProductForMultiIfiles = product;
                        }
                    }

                    if (productFilter.accept(product) && regexFileFilter.accept(file)) {
                        tmpArrayList.add(file);
                    } else {
                        final String message = String.format("Product [%s] is not a valid source.",
                                product.getFileLocation().getCanonicalPath());
                        handleError(window, message);
                        SeadasLogger.getLogger().warning(" product is hidden: " + new Boolean(product.getFileLocation().isHidden()).toString());
                        product.dispose();
                    }
                } catch (IOException e) {

                    handleError(window, e.getMessage());
                } catch (Exception e) {
                    if (sampleProductForMultiIfiles != null) {
                        sampleProductForMultiIfiles.dispose();
                    }
                    handleError(window, e.getMessage());
                    e.printStackTrace();
                }
            }
            setSelectedMultiFileList(tmpArrayList);
        }

        public void setSelectedMultiFileList(ArrayList<File> tmpArrayList) {
            files = new File[tmpArrayList.size()];
            tmpArrayList.toArray(files);

            File fileListFile = new File(currentDirectory, "_inputFiles.lst");

            StringBuilder fileNames = new StringBuilder();
            for (File file : files) {
                fileNames.append(file.getAbsolutePath() + "\n");
            }
            FileWriter fileWriter = null;
            try {

                fileWriter = new FileWriter(fileListFile);
                fileWriter.write(fileNames.toString());
                fileWriter.close();
            } catch (IOException ioe) {
            }
            Product product = new Product(fileListFile.getName(), "DummyType", 10, 10);
            product.setFileLocation(fileListFile);
            product.setDescription(fileNames.toString());
            setSelectedProduct(product);
            currentDirectory = fileChooser.getCurrentDirectory();
            appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR,
                    currentDirectory.getAbsolutePath());
        }

        private void handleError(final Component component, final String message) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    //@Override
                    //JOptionPane.showMessageDialog(component, message, "Error", JOptionPane.ERROR_MESSAGE);
                    //JOptionPane.showMessageDialog(<Window>component, message, "test", JOptionPane.ERROR_MESSAGE);
                }
            });
        }


    }


    private static class ProductListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            final Component cellRendererComponent =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (cellRendererComponent instanceof JLabel && value instanceof Product) {
                final JLabel label = (JLabel) cellRendererComponent;
                final Product product = (Product) value;
                label.setText(product.getDisplayName());
                label.setToolTipText(product.getDescription());
            }

            return cellRendererComponent;
        }
    }

    /**
     * To let the popup menu be wider than the closed combobox.
     * Adapted an idea from http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6257236
     */
    private static class ProductPopupMenuListener implements PopupMenuListener {

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            JComboBox box = (JComboBox) e.getSource();
            Object comp = box.getUI().getAccessibleChild(box, 0);
            if (!(comp instanceof JPopupMenu)) {
                return;
            }
            JComponent scrollPane = (JComponent) ((JPopupMenu) comp)
                    .getComponent(0);

            Dimension size = new Dimension();
            size.width = scrollPane.getPreferredSize().width;
            final int boxItemCount = box.getModel().getSize();
            for (int i = 0; i < boxItemCount; i++) {
                Product product = (Product) box.getModel().getElementAt(i);
                final JLabel label = new JLabel();
                label.setText(product.getDisplayName());
                size.width = Math.max(label.getPreferredSize().width, size.width);
            }
            size.height = scrollPane.getPreferredSize().height;
            scrollPane.setPreferredSize(size);
            scrollPane.setMaximumSize(size);
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        }
    }

    private static class AllProductFilter implements ProductFilter {

        @Override
        public boolean accept(Product product) {
            return true;
        }
    }

    private class RegexFileFilter extends FileFilter {

        private String regex;

        public RegexFileFilter() {
            this(null);
        }

        public RegexFileFilter(String regex) throws IllegalStateException {
            SeadasLogger.getLogger().info("regular expression: " + regex);

            if (regex == null) {
                return;
            }

            //Replace wildcards with regular expression.
            if (regex.indexOf("*") != -1) {
                regex = regex.replaceAll("\\*", ".*");
            }
            if (regex.trim().length() == 0) {

                //throw new IllegalStateException();
                return;
            }

            this.regex = ".*" + regex + ".*";

        }

        /* (non-Javadoc)
        * @see java.io.FileFilter#accept(java.io.File)
        */
        public boolean accept(File pathname) {

            if (regex == null) {
                return true;
            }
            SeadasLogger.getLogger().info("regex: " + (pathname.isFile() && pathname.getName().matches(this.regex)));
            return (pathname.isFile() && pathname.getName().matches(this.regex));
        }

        public String getDescription() {
            return "Files matching regular expression: '" + regex + "'";
        }

        public void ensureFileIsVisible(JFileChooser fc, File f) {
            ensureFileIsVisible(fc, f);
            //ensureFileIsVisible(f, true);
        }
    }
}
