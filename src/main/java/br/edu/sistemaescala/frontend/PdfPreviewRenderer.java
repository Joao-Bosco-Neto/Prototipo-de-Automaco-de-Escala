package br.edu.sistemaescala.frontend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.scene.image.Image;

/**
 * Rasteriza a primeira pagina de um PDF em memoria para uma {@link Image} do
 * JavaFX, alimentando a pre-visualizacao da tela de exportacao (issue #43/#52).
 *
 * <p>O OpenPDF, biblioteca padrao de geracao do projeto, so escreve PDF — nao
 * le nem renderiza. O PDFBox entra <b>apenas</b> aqui, no papel oposto: abrir o
 * PDF que o {@code GeradorPdfService} acabou de gerar e transformar a pagina em
 * pixels.</p>
 *
 * <p>A conversao passa por PNG em memoria ({@link ImageIO}) em vez de
 * {@code SwingFXUtils}, para nao arrastar o modulo {@code javafx-swing} so por
 * causa disto.</p>
 *
 * <p>Metodos sao puros e sem estado de UI: devem rodar na thread de fundo da
 * {@code Task}, nunca na JavaFX Application Thread.</p>
 */
public final class PdfPreviewRenderer {

    /** DPI da rasterizacao: nitido o suficiente para leitura na tela sem estourar memoria. */
    private static final float DPI_PREVIEW = 150f;

    private PdfPreviewRenderer() {
        // classe utilitaria: nao deve ser instanciada
    }

    /**
     * Renderiza a primeira pagina do {@code pdf} como imagem pronta para um
     * {@code ImageView}.
     *
     * @param pdf documento completo em bytes (saida de {@code GeradorPdfService.gerarEmMemoria})
     * @return a primeira pagina rasterizada
     * @throws IOException se o PDF nao puder ser lido ou a pagina nao puder ser renderizada
     */
    public static Image renderizarPrimeiraPagina(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFRenderer renderizador = new PDFRenderer(documento);
            BufferedImage pagina = renderizador.renderImageWithDPI(0, DPI_PREVIEW, ImageType.RGB);

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(pagina, "png", png);
            return new Image(new ByteArrayInputStream(png.toByteArray()));
        }
    }
}
