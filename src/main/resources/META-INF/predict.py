import joblib
import sys
import numpy as np

# TODO add some more checks which produce better user-facing messages
# TODO if we have 1 sample as input it breaks since it thinks it is an array rather than a matrix with 1 row
def main():
    args = sys.argv
    scaler_path = args[1]
    model_path = args[2]

    X = np.loadtxt(sys.stdin)

    model = joblib.load(model_path)
    scaler = joblib.load(scaler_path)

    X = scaler.transform(X)
    y = model.predict(X)

    for pred in y:
        print(pred.astype(bool))

if __name__ == '__main__':
    main()
