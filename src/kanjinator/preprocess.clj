(ns kanjinator.preprocess
  (:require
   [clojure.string :as str])
  (:import
   (java.awt Color)
   (java.awt.image BufferedImage)
   (java.awt.image BufferedImage)
   (java.io File)
   (java.io ByteArrayOutputStream File)
   (javax.imageio ImageIO)
   (javax.imageio ImageIO)
   (org.opencv.core Core Mat MatOfByte Size)
   (org.opencv.highgui HighGui)
   (org.opencv.imgcodecs Imgcodecs)
   (org.opencv.imgproc Imgproc)))

(defn image->mat [^BufferedImage image]
  (let [os (new ByteArrayOutputStream)]
    (ImageIO/write image "png" os)
    (.flush os)
    (Imgcodecs/imdecode (MatOfByte. (.toByteArray os)) Imgcodecs/IMREAD_UNCHANGED)))

(defn mat->image [mat]
  (HighGui/toBufferedImage mat))

(defn- add-margin [^BufferedImage img]
  (let [padding 50
        new-w (+ (.getWidth img) padding)
        new-h (+ (.getHeight img) padding)
        padded (new BufferedImage new-w new-h (.getType img))
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
  (Core/bitwise_not img img)
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

(defn- threshold [^Mat img]
  (Imgproc/threshold img img 0 255 (+ Imgproc/THRESH_BINARY Imgproc/THRESH_OTSU))
  img)

(defn- increase-contrast [^Mat img]
  (Core/convertScaleAbs img img 1.5 0)
  img)

(defn- save-image [^BufferedImage img ^String path]
  (ImageIO/write ^BufferedImage img ^String (peek (str/split path #"\.")) (File. path)))

(def ^:private save-image-fn
  (if (#{"true" "TRUE" "1"} (System/getenv "SAVE_DBG_IMG"))
    #(doto % (save-image "debug.png"))
    identity))

(def ^:private default-process-pipeline-functions [scale grayscale invert-if-needed])
(def default-process-pipeline (apply comp (reverse default-process-pipeline-functions)))

(defn preprocess-image [^BufferedImage img process]
  (-> img
      (image->mat)
      (process)
      (mat->image)
      ;;(add-margin)
      (save-image-fn)))
