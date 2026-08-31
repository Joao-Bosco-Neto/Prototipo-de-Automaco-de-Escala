package br.edu.sistemaescala.frontend;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.service.CoberturaListagemItem;
import br.edu.sistemaescala.backend.service.CoberturaService;
import br.edu.sistemaescala.backend.service.SubstitutoDisponivel;
import br.edu.sistemaescala.frontend.controller.CoberturaController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/** Descartável: renderiza só a tela de cobertura e salva um PNG. */
public class DebugCoberturaApp extends Application {

    private static final CoberturaService STUB = new CoberturaService() {
        public List<EscalaFuncionario> listarEscaladosNaData(LocalDate d) { return List.of(); }
        public List<MotivoCobertura> listarMotivos() { return List.of(); }
        public List<SubstitutoDisponivel> listarSubstitutos(EscalaFuncionario a) { return List.of(); }
        public List<SubstitutoDisponivel> listarSubstitutos(EscalaFuncionario a, EscalaFuncionario c) { return List.of(); }
        public List<CoberturaListagemItem> listarCoberturasParaListagem(YearMonth m) { return List.of(); }
        public EscalaFuncionario registrar(EscalaFuncionario a, Funcionario s, Integer m, String o, boolean b) { return null; }
        public EscalaFuncionario editar(EscalaFuncionario c, EscalaFuncionario a, Funcionario s, Integer m, String o, boolean b) { return null; }
        public void excluir(EscalaFuncionario c) { }
    };

    @Override
    public void start(Stage stage) throws Exception {
        CoberturaController controller = new CoberturaController(STUB);
        Scene cena = new Scene((javafx.scene.Parent) controller.criarTela(), 900, 900);
        cena.getStylesheets().add(getClass().getResource("/frontend/css/app.css").toExternalForm());
        stage.setScene(cena);
        stage.show();

        Platform.runLater(() -> {
            WritableImage img = cena.snapshot(null);
            try {
                int w = (int) img.getWidth();
                int h = (int) img.getHeight();
                BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                PixelReader pr = img.getPixelReader();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        bi.setRGB(x, y, pr.getArgb(x, y));
                    }
                }
                File out = new File("target/debug-cobertura.png");
                ImageIO.write(bi, "png", out);
                System.out.println("SAVED " + out.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
