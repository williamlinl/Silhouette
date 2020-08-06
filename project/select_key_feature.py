import sys
import csv
from minepy import MINE
import numpy as np
from scipy.stats import pearsonr
from sklearn.feature_selection import SelectFromModel
from sklearn.feature_selection import VarianceThreshold
from sklearn.linear_model import LinearRegression


class SelectKeyFeature(object):

    def __init__(self, training_data_in=[], data_file=None, save_file=None, model=None):
        '''Load training data from csv file into training_data[[cores, machine, fraction, time]...].'''
        self.save_file = save_file
        self.select_model = model
        self.training_data = []
        self.training_data.extend(training_data_in)
        if data_file:
            with open(data_file, 'rb') as csvfile:
                reader = csv.reader(csvfile, delimiter=' ')
                for row in reader:
                    if row[0][0] != '&':
                        parts = row[0].split(',')
                        cores = int(parts[0])
                        machine = int(parts[1])
                        fraction = float(parts[2])
                        time = float(parts[3])
                        self.training_data.append([cores, machine, fraction, time])

    @staticmethod
    def _get_feature(training_point):
        '''Compute the feature for a given training data point[cores, machine, fraction, time].'''
        cores = training_point[0]
        machine = training_point[1]
        fraction = training_point[2]
        time = training_point[3]
        return [time, float(fraction) / float(cores), 1 / float(cores),
                float(machine), np.log(machine), np.square(machine)]

    @staticmethod
    def _feature_filter(training_features):
        '''Feature sort by mutual information scores.'''
        m = MINE()
        data_row = training_features.shape[1]
        scores = []
        scores_sum = 0
        for i in range(1, data_row):
            m.compute_score(training_features[:, i], training_features[:, 0])
            scores_sum += m.mic()
            scores.append((i, m.mic()))
        scores_mean = scores_sum / data_row
        for score in scores:
            if score[1] < scores_mean:
                scores.remove(score)
        scores.sort()
        return scores

    def _save_feature(self, selected_features):
        '''Save training points as (time, term0, term1, ..., termn) in .csv file'''
        if self.save_file:
            with open(self.save_file, 'wb') as csvfile:
                writer = csv.writer(csvfile, csvfile, delimiter=',',
                            quotechar='|', quoting=csv.QUOTE_MINIMAL)
                for i in range(0, selected_features.shape[0]):
                    row = np.append([self.training_data[i][3], 1.0], selected_features[i]).tolist()
                    writer.writerow(row)

    def _transform_feature(self, points):
        '''Transform selected feature list into [1.0, selected terms] '''
        all_features = np.array([self._get_feature(point) for point in points])
        # print all_features
        return list(self._add_features(self.select_model.transform(all_features[:, 1: 6]), all_features[:, 0]))
        # return list(self._add_features(all_features[:, 1: 6], all_features[:, 0]))

    @staticmethod
    def _add_features(selected_features, time):
        '''Add serial computation feature to selected features'''
        for i in range(0, selected_features.shape[0]):
            yield np.append([time[i],1.0], selected_features[i]).tolist()

    def _build_model(self):
        '''Build a SelectFromModel features selector (LinearRegression).'''
        all_data_features = np.array([self._get_feature(point) for point in self.training_data])
        lr = LinearRegression()
        lr.fit(all_data_features[:, 1: 6], all_data_features[:, 0])
        self.select_model = SelectFromModel(lr, threshold="0.1*mean", prefit=True)

    def select(self):
        print "Select features ..."
        all_data_features = np.array([self._get_feature(point) for point in self.training_data])
        # Feature selection using SelectFromModel
        selected_features = self.select_model.transform(all_data_features[:, 1: 6])
        self._save_feature(selected_features)
        # # Remove features with low variance.
        # print "Variance"
        # high_variance_features = VarianceThreshold(threshold=(.8 * (1 - .8)))
        # print high_variance_features.fit_transform(all_data_features[:, 1 : 7])
        #
        # # # Calculate Pearson.
        # print "Pearson"
        # for i in range(1, all_data_features.shape[1]):
        #     print pearsonr(all_data_features[:, i], all_data_features[:, 0])
        # # Mutual information and maximal information coefficient (MIC)
        # print "Mutual"
        # m = MINE()
        # for i in range(1, all_data_features.shape[1]):
        #     m.compute_score(all_data_features[:, i], all_data_features[:, 0])
        #     print m.mic()
        # print all_data_features
        # return self._feature_filter(all_data_features)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print "Usage <select_key_feature.py> <train_data.csv>"
        sys.exit(0)
    feature_selector = SelectKeyFeature(data_file=sys.argv[1], save_file=sys.argv[2])
    feature_selector.select()
    # 1: float(fraction) / float(cores)
    # 2: np.sqrt(fraction) / float(cores)
    # 3: float(machine)
    # 4: np.log(machine)
    # 5: np.square(machine)]
    # print "Term, Mutual information"
    # for score in scores:
    #     print score




