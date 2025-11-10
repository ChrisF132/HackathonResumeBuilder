package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.bootstrapfx.BootstrapFX;

import javafx.geometry.Insets;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.nio.file.Path;

public class Main extends Application {

    private boolean showUnderscore = true;

    private GeminiServer geminiClient;

    private UserInfo userInfo = new UserInfo();

    @Override
    public void start(Stage stage) throws Exception {
        Font font = Font.loadFont(getClass().getResourceAsStream("/org/example/fonts/Raleway.ttf"), 14);
        System.out.println("Loaded font: " + (font != null ? font.getName() : "null"));
        try {
            String keyPath = getClass().getResource("/org/example/API_KEY.txt").getPath();
            geminiClient = new GeminiServer("/org/example/API_KEY.txt", GeminiServer.Mode.RESUME);
        } catch (Exception e) {
            System.err.println("Failed to load API key: " + e.getMessage());
            Platform.exit();
            return;
        }

        //SCREEN 1

        //Button + Text Setup
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setSpacing(15);

        //TextFields
        Label title = new Label("AI Resume Builder");

        TextField nameField = new TextField();
        nameField.setPromptText("Name");

        nameField.setMaxWidth(250);

        TextField emailField = new TextField();
        emailField.setPromptText("E-mail");
        emailField.setMaxWidth(250);

        TextField phoneNumberField = new TextField();
        phoneNumberField.setPromptText("Phone Number");
        phoneNumberField.setMaxWidth(250);

        TextField cityStateField = new TextField();
        cityStateField.setPromptText("City, State");
        cityStateField.setMaxWidth(250);

        TextField aboutMeField = new TextField();
        aboutMeField.setPromptText("About Me - Type Some Bullet Points About Yourself");
        aboutMeField.setAlignment(Pos.TOP_LEFT);
        aboutMeField.setMinHeight(100);
        aboutMeField.setMaxWidth(250);

        TextField qualificationsField = new TextField();
        qualificationsField.setPromptText("Qualifications");
        qualificationsField.setAlignment(Pos.TOP_LEFT);
        qualificationsField.setMinHeight(100);
        qualificationsField.setMaxWidth(250);

        //

        // Buttons
        Button button = new Button("Build Your Resume!");
        button.setOnAction(e -> {
            userInfo.setName(nameField.getText());
            userInfo.setEmail(emailField.getText());
            userInfo.setPhone(phoneNumberField.getText());
            userInfo.setCityState(cityStateField.getText());
            userInfo.setAbout(aboutMeField.getText());
            userInfo.setQuals(qualificationsField.getText());

            VBox chatContainer = new VBox(10);
            chatContainer.setPadding(new Insets(10));
            ScrollPane scrollPane = new ScrollPane(chatContainer);
            scrollPane.setFitToWidth(true);

            // continue building chat UI
            TextField inputField = new TextField();
            inputField.setPromptText("Type a message...");
            Button sendButton = new Button("Send");

            HBox inputArea = new HBox(10, inputField, sendButton);
            HBox.setHgrow(inputField, Priority.ALWAYS);
            inputArea.setPadding(new Insets(10));

            Button downloadButton = new Button("Download as Docx File");

            VBox chatLayout = new VBox(10, scrollPane, inputArea, downloadButton);
            chatLayout.setPadding(new Insets(10));

            Scene chatScene = new Scene(chatLayout, 1400,900);
            chatScene.getStylesheets().addAll(BootstrapFX.bootstrapFXStylesheet(),
                    getClass().getResource("style.css").toExternalForm());

            stage.setScene(chatScene);

            // Gemini mode switch and initial AI prompt
            geminiClient.setMode(GeminiServer.Mode.REFINEMENT);
            String initialPrompt = "Build a professional resume using the following info:\n"
                    + "Name: " + userInfo.getName() + "\n"
                    + "Email: " + userInfo.getEmail() + "\n"
                    + "Phone: " + userInfo.getPhone() + "\n"
                    + "City/State: " + userInfo.getCityState() + "\n"
                    + "About Me: " + userInfo.getAbout() + "\n"
                    + "Qualifications: " + userInfo.getQuals();

            geminiClient.sendMessageAsync(initialPrompt, chatContainer);

            new Thread(() -> {
                try {
                    Thread.sleep(8000); // wait for Gemini to finish
                    Path saved = geminiClient.exportLatestResume(userInfo);
                    if (saved != null) {
                        Platform.runLater(() -> {
//                            geminiClient.sendMessageAsync(
//                                    "Your AI-generated resume has been saved to: " + saved,
//                                    chatContainer
//                            );
                            downloadButton.setDisable(false);
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();

            // handle user chat messages
            sendButton.setOnAction(ev -> {
                String userMessage = inputField.getText().trim();
                if (!userMessage.isEmpty()) {
                    geminiClient.sendMessageAsync(userMessage, chatContainer);
                    inputField.clear();
                }
            });

            downloadButton.setOnAction(ev -> {
                Path path = geminiClient.exportLatestResume(userInfo);
//                if (path != null) {
//                    geminiClient.sendMessageAsync(
//                            "Resume saved again to: " + path, chatContainer
//                    );
//                } else {
//                    geminiClient.sendMessageAsync(
//                            "No resume available yet.", chatContainer
//                    );
//                }
            });
        });


        // Add everything to Scene 1
        root.getChildren().addAll(title,nameField,emailField,phoneNumberField,cityStateField,aboutMeField,qualificationsField,button);


        // Scene 1 + Stage Setup
        Scene scene = new Scene(root,1400,900);
        scene.getStylesheets().addAll(BootstrapFX.bootstrapFXStylesheet(), getClass().getResource("style.css").toExternalForm());
        title.getStyleClass().addAll("text-primary", "display-3");
        button.getStyleClass().addAll("btn", "btn-primary", "rounded-pill");
        title.setFont(font);
        stage.setTitle("AI Resume Builder");
        stage.setScene(scene);
        stage.show();

        //SCREEN 2

        Label coolTypingTitleText = new Label();
        coolTypingTitleText.setWrapText(true);
        coolTypingTitleText.setStyle("-fx-text-alignment: center; -fx-line-spacing: 5;");
        String testText = "Build an Awesome Resume With HackQU's AI Resume Builder";
        Timeline timeline = new Timeline();
        for (int i = 0; i < testText.length(); i++) {
            final int index = i;
            KeyFrame keyFrame = new KeyFrame(Duration.millis(100 * i), e -> {
                coolTypingTitleText.setText(testText.substring(0, index + 1));
            });
            timeline.getKeyFrames().add(keyFrame);
        }

        Timeline blinkTimeline = new Timeline(
                new KeyFrame(Duration.millis(500), e -> {
                    if (showUnderscore) {
                        coolTypingTitleText.setText("Build an Awesome Resume With HackQU's AI Resume Builder_");
                    } else {
                        coolTypingTitleText.setText("Build an Awesome Resume With HackQU's AI Resume Builder");
                    }
                    showUnderscore = !showUnderscore; // flip flag
                })
        );

        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
        blinkTimeline.play();


        timeline.play();
        VBox root2 = new VBox();
        root2.setAlignment(Pos.CENTER);
        root2.setSpacing(50);
        Button startButton = new Button("Get Started");

        startButton.setOnAction(e->{
            stage.setScene(scene);
        });

        coolTypingTitleText.getStyleClass().addAll("text-primary", "display-5");
        root2.getChildren().addAll(coolTypingTitleText,startButton);
        Scene scene2 = new Scene(root2,1400,900);
        scene2.getStylesheets().addAll(BootstrapFX.bootstrapFXStylesheet(), getClass().getResource("style.css").toExternalForm());
        coolTypingTitleText.getStyleClass().addAll("text-primary", "display-3");
        startButton.getStyleClass().addAll("btn", "btn-primary", "rounded-pill");
        stage.setScene(scene2);
        stage.show();
    }

    public static void main(String[] args) {
        System.out.println("Test works!");
        launch(args);
    }
}