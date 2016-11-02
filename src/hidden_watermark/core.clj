(ns hidden-watermark.core
  (:import [java.awt Image]
           [java.awt.image BufferedImage]
           [edu.emory.mathcs.jtransforms.fft DoubleFFT_2D RealFFTUtils_2D])
  (:require [clojure.java.io :as io]
            [mikera.image.core :as img]
            [clojure.core.matrix :as mat]))

(def path "test.jpeg")

(defn buffer-image [path]
  (let[file (io/file path)]
    (img/load-image file)))

(defn magnitude ^double [^double r ^double i] (Math/sqrt (+ (* r r) (* i i))))

(defn phase ^double [^double r ^double i] (Math/atan2 i r))

(defn real ^double [^double r ^double i] r)

(defn imaginary ^double [^double r ^double i] i)


(defn dft-fn [ri-fn M]
    (let [w (mat/column-count M)
          h (mat/row-count M)
          ds (mat/to-double-array M)
          ^doubles result (make-array Double/TYPE (* 2 (count ds)))
          dfft-2d (DoubleFFT_2D. h w)
          unpacker (RealFFTUtils_2D. h w)
          nM (mat/new-matrix h w)]
      (System/arraycopy ds 0 result 0 (count ds))
      (.realForward dfft-2d result)
      (dotimes [y h]
        (dotimes [x w]
          (let [r (.unpack unpacker y (* 2 x) result 0)
                i (.unpack unpacker y (inc (* 2 x)) result 0)]
            (mat/mset! nM y x (ri-fn r i)))))
      nM))

(def dft-magnitudes (partial dft-fn magnitude))
(def dft-phases (partial dft-fn phase))
(def dft-reals (partial dft-fn real))
(def dft-imaginarys (partial dft-fn imaginary))

(defn idft-ri-fn [ri-fn R I]
  (let [w (mat/column-count R)
        hw (int (/ w 2))
        h (mat/row-count R)
        hh (int (/ h 2))
        ^doubles result (make-array Double/TYPE (* 2 w h))
        dfft-2d (DoubleFFT_2D. h w)
        nM (mat/new-matrix h w)]
    (dotimes [y h]
      (dotimes [x h]
        (aset result (+ (* y 2 h) (* x 2)) ^double (mat/mget R y x))
        (aset result (+ (* y 2 h) (inc (* x 2))) ^double (mat/mget I y x))))
    (.complexInverse dfft-2d result true)
    (dotimes [y h]
      (dotimes [x w]
        (let [r (aget result (+ (* y 2 h) (* x 2)))
              i (aget result (+ (* y 2 h) (inc (* x 2))))]
          (mat/mset! nM y x (ri-fn r i)))))
    nM))

(def idft-ri-reals (partial idft-ri-fn real))

(defn rgb-to-ngrey [val]
  (/ (+ (* 0.2126 (bit-and 0xFF (bit-shift-right val 16)))
        (* 0.7152 (bit-and 0xFF (bit-shift-right val 8)))
        (* 0.0722 (bit-and 0xFF val)))
     255.0))

(defn rgb-to-grey [val]
  (+ (* 0.2126 (bit-and 0xFF (bit-shift-right val 16)))
     (* 0.7152 (bit-and 0xFF (bit-shift-right val 8)))
     (* 0.0722 (bit-and 0xFF val))))

(defn ngrey-to-rgb [^double val]
  (let [ival (int (Math/round (* val 255)))]
    (bit-or 0xFF000000
            (bit-shift-left ival 16)
            (bit-shift-left ival 8)
            ival)))

(defn image-to-matrix [^BufferedImage bi rgb-to-val-fn]
  (let [h (.getHeight bi)
        w (.getWidth bi)
        M (mat/new-matrix h w)]
    (dotimes [y h]
      (dotimes [x w]
        (mat/mset! M y x (rgb-to-val-fn (.getRGB bi x y)))))
    M))


(defn matrix-to-image [M val-to-rgb-fn]
  (let [w (mat/column-count M)
        h (mat/row-count M)
        bi (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
    (dotimes [y h]
      (dotimes [x w]
        (let [v (mat/mget M y x)]
          (.setRGB bi x y (unchecked-int (val-to-rgb-fn v))))))
    bi))

(defn normalize! [M]
  (let [^double mn (mat/emin M)
        ^double mx (mat/emax M)
        r (double (- mx mn))]
    (mat/emap! (fn [^double d] (/ (- d mn) r)) M)))

(def eg-1 [0.0  0.0    0.0    0.0    0.0  0.0  0.0  0.0
           0.0  0.0   70.0   80.0   90.0  0.0  0.0  0.0
           0.0  0.0   90.0  100.0  110.0  0.0  0.0  0.0
           0.0  0.0  110.0  120.0  130.0  0.0  0.0  0.0
           0.0  0.0  130.0  140.0  150.0  0.0  0.0  0.0
           0.0  0.0    0.0    0.0    0.0  0.0  0.0  0.0
           0.0  0.0    0.0    0.0    0.0  0.0  0.0  0.0
           0.0  0.0    0.0    0.0    0.0  0.0  0.0  0.0])

