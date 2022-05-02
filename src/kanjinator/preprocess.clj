(ns kanjinator.preprocess
  (:require
   [clojure.string :as str])
  (:import
   [java.awt.image BufferedImage]
   [java.io ByteArrayOutputStream File]
   [javax.imageio ImageIO]
   [org.opencv.core Core Mat MatOfByte Size Scalar]
   [org.opencv.highgui HighGui]
   [org.opencv.imgcodecs Imgcodecs]
   [org.opencv.imgproc Imgproc]))

(defn image->mat [^BufferedImage image]
  (let [os (new ByteArrayOutputStream)]
    (ImageIO/write image "png" os)
    (.flush os)
    (Imgcodecs/imdecode (MatOfByte. (.toByteArray os)) Imgcodecs/IMREAD_UNCHANGED)))

(defn mat->image [mat]
  (HighGui/toBufferedImage mat))

(defn scale [^Mat img]
  (Imgproc/pyrUp img img)
  img)

(defn grayscale [^Mat img]
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

(defn remove-noise [^Mat img]
  (let [kernel (Imgproc/getStructuringElement Imgproc/MORPH_RECT (Size. 2 2))]
    (Imgproc/morphologyEx img img Imgproc/MORPH_OPEN kernel))
  img)

(defn threshold [^Mat img]
  (Imgproc/threshold img img 0 255 (+ Imgproc/THRESH_BINARY Imgproc/THRESH_OTSU))
  img)

(defn increase-contrast [^Mat img]
  (Core/convertScaleAbs img img 1.5 0)
  img)

(defn add-white-margin [^Mat img]
  (Core/copyMakeBorder img img 20 20 20 20 Core/BORDER_CONSTANT (Scalar. 255 255 255))
  img)

(defn- save-image [^BufferedImage img ^String path]
  (ImageIO/write ^BufferedImage img ^String (peek (str/split path #"\.")) (File. path)))

(def ^:private save-image-fn
  (if (#{"true" "TRUE" "1"} (System/getenv "SAVE_DBG_IMG"))
    #(doto % (save-image "debug.png"))
    identity))

(defn- make-process-pipeline [functions]
  (->> functions
       (reverse)
       (apply comp)))

(defn preprocess-image [^BufferedImage img process-functions]
  (-> img
      (image->mat)
      ((make-process-pipeline process-functions))
      (mat->image)
      (save-image-fn)))
