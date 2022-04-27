(ns kanjinator.core
  (:gen-class)
  (:require
   [clojure.string :as str]
   [tinysegmenter.core :refer [segment]])
  (:import
   [nu.pattern OpenCV]
   [java.awt Frame Graphics2D Point Color Robot Rectangle]
   [java.awt.event MouseListener MouseMotionListener MouseEvent WindowEvent]
   [java.awt.image BufferedImage]
   [java.awt.image BufferedImage]
   [java.io ByteArrayOutputStream File]
   [java.io File]
   [javax.imageio ImageIO]
   [javax.imageio ImageIO]
   [javax.swing JFrame JPanel  WindowConstants SwingUtilities]
   [net.sourceforge.tess4j Tesseract]
   [org.opencv.core MatOfByte Mat Core Size]
   [org.opencv.highgui HighGui]
   [org.opencv.imgproc Imgproc]
   [org.opencv.imgcodecs Imgcodecs]))

(defn image->mat [^BufferedImage image]
  (let [os (new ByteArrayOutputStream)]
    (ImageIO/write image "png" os)
    (.flush os)
    (Imgcodecs/imdecode (MatOfByte. (.toByteArray os)) Imgcodecs/IMREAD_UNCHANGED)))

(defn mat->image [mat]
  (HighGui/toBufferedImage mat))

(defprotocol SaveImage
  (save-image [img path]))

(extend-protocol SaveImage
  Mat
  (save-image [img path]
    (Imgcodecs/imwrite path img))
  BufferedImage
  (save-image [^BufferedImage img ^String path]
    (ImageIO/write ^BufferedImage img ^String (peek (str/split path #"\.")) (File. path))))

(defn rect->xywh [rect]
  (let [{:keys [^Point start ^Point end]} rect]
    (when (and start end)
      [(min (.getX start) (.getX end))
       (min (.getY start) (.getY end))
       (abs (- (.getX end) (.getX start)))
       (abs (- (.getY end) (.getY start)))])))

(def ^Tesseract tesseract
  (doto (new Tesseract)
    (.setDatapath "resources/tessdata")
    (.setLanguage "jpn")
    (.setPageSegMode 1)
    (.setOcrEngineMode 1)))

(defn perform-ocr [^BufferedImage img]
  (.doOCR tesseract img))

(defn- add-margin [^BufferedImage img]
  (let [padding 50
        new-w   (+ (.getWidth img) padding)
        new-h   (+ (.getHeight img) padding)
        padded  (new BufferedImage new-w new-h (.getType img))
        g (.getGraphics padded)]
    (doto g
      (.setColor Color/white)
      (.fillRect 0 0 new-w new-h)
      (.drawImage img (/ padding 2) (/ padding 2) nil)
      (.dispose))
    padded))

(defn- scale [^Mat img]
  (Imgproc/pyrUp img img)
  img)

(defn- grayscale [^Mat img]
  (Imgproc/cvtColor img img Imgproc/COLOR_BGR2GRAY)
  img)

(defn- invert [^Mat img]
  (Core/bitwise-not img img)
  img)

(defn- mean-brightness [^Mat img]
  (let [^doubles vals (.-val (Core/mean img))]
    (areduce vals i ret 0.0 (+ ret (aget vals i)))))

(defn invert-if-needed [^Mat img]
  (let [brightness (mean-brightness img)]
    (if (< brightness 100)
      (invert img)
      img)))

(defn- remove-noise [^Mat img]
  (let [kernel (Imgproc/getStructuringElement Imgproc/MORPH_RECT (Size. 2 2))]
    (Imgproc/morphologyEx img img Imgproc/MORPH_OPEN kernel))
  img)

(defn- bilateral-filter [^Mat img]
  (let [dest (Mat.)]
    (Imgproc/bilateralFilter img dest 5 55.0 60.0)
    dest))

(defn- threshold [^Mat img]
  (Imgproc/threshold img img 0 255 (+ Imgproc/THRESH_BINARY_INV Imgproc/THRESH_OTSU))
  img)

(defn- preprocess-image [^BufferedImage img]
  (-> img
      (image->mat)
      (scale)
      (grayscale)
      (invert-if-needed)
      (remove-noise)
      (mat->image)
      (add-margin)
      (doto (save-image "kek.png"))))

(defn- make-screenshot [window rect]
  (let [translate  #(when %
                      (doto (.clone ^Point %)
                        (SwingUtilities/convertPointToScreen window)))
        rect       (-> rect
                       (update :start translate)
                       (update :end translate))
        robot      (Robot.)
        [x y w h]  (rect->xywh rect)]
    (when (and x y w h)
      (-> robot
          (.createScreenCapture (Rectangle. x y w h))
          (preprocess-image)))))

(defn- render-selection-rect [^Graphics2D g state]
  (when-let [[x y w h] (-> state :rect rect->xywh)]
    (doto g
      (.setColor (Color. 30 130 200 100))
      (.fillRect x y w h))))

(defn- process-selection [^JFrame window state]
  (when-let [screenshot-rect (:screenshot-rect state)]
    (let [screenshot (make-screenshot window screenshot-rect)]
      (->> screenshot
           (perform-ocr)
           (remove #(Character/isWhitespace ^char %))
           (segment)
           (prn))
      (.dispatchEvent window (WindowEvent. window WindowEvent/WINDOW_CLOSING)))))

(defn draw-panel [state ^JFrame window]
  (let [panel (proxy [JPanel] []
                (paintComponent [^Graphics2D g]
                  ;; (proxy-super paintComponent g)
                  (let [state @state]
                    (render-selection-rect g state)
                    (process-selection window state))))]
    (.setBackground panel (Color. 0 0 0 0))
    panel))

(defn mouse-motion-listener [rect ^JFrame frame]
  (proxy [MouseMotionListener] []
    (mouseDragged [^MouseEvent e]
      (swap! rect
             (fn [rect]
               (-> rect
                   (update-in [:rect :start] (fnil identity (.getPoint e)))
                   (assoc-in [:rect :end] (.getPoint e)))))
      ;; update the frame to draw the current rect
      (.update frame (.getGraphics frame)))
    (mouseMoved [^MouseEvent e])))

(defn mouse-listener [state]
  (proxy [MouseListener] []
    (mouseReleased [^MouseEvent e]
      (swap! state (fn [{:keys [rect]}] {:screenshot-rect rect}))
      ;; force redraw so we can make a screenshot in the paintComponent method of the JPanel
      ;; we can't do it here, because then the selection rectangle would be in the screenshot
      ;; aswell, which can screw with the OCR
      (let [window (.getComponent e)]
        (.update window (.getGraphics window))))
    (mousePressed [e])
    (mouseClicked [e])
    (mouseEntered [e])
    (mouseExited [e])))

(defn -main []
  (OpenCV/loadLocally)
  (let [frame  (new JFrame)
        state  (atom {})
        panel  (draw-panel state frame)]
    (doto frame
      (.setDefaultCloseOperation WindowConstants/EXIT_ON_CLOSE)
      (.setLocationRelativeTo nil)
      (.setUndecorated true)
      (.setBackground (Color. 0 0 0 0))
      (.setExtendedState Frame/MAXIMIZED_BOTH))

    (doto frame
      (.addMouseMotionListener (mouse-motion-listener state frame))
      (.addMouseListener (mouse-listener state))
      (.setContentPane panel)
      (.pack)
      (.setVisible true))))
