import numpy as np
import scipy.optimize as solver
from select_key_feature import SelectKeyFeature
import sys
import csv


class ConfigurationSelector(object):

    def __init__(self, training_data_in=[]):
        '''Load training data from csv file into training_data[[machine, fraction, time]...].'''
        self.training_data = []
        self.training_data.extend(training_data_in)

    @staticmethod
    def _get_features(training_sample):
        ''' Compute the features for a given sample. Sample is expected'''
        return np.array(training_sample[1:7])

    def build(self):
        print "Basic model building ..."
        all_features = np.array([self._get_features(sample) for sample in self.training_data])
        # print all_features
        labels = np.array([term[0] for term in self.training_data])
        basic_model = solver.nnls(all_features, labels)
        # print all_features
        # print basic_model[0]
        errors = []
        for p in self.training_data:
            predicted = np.array(self._get_features(p).dot(basic_model[0]))
            # print self._get_features(p)
            print predicted
            errors.append(predicted / p[0])
        print "Average training error %f%%" % ((np.mean(errors) - 1.0)*100.0)
        return basic_model


if __name__ == "__main__":
    # if len(sys.argv) != 2:
    #     print "Usage <configuration_selector.py> <train_data.csv>"
    #     sys.exit(0)

    # Load training data from .csv file.
    feature_selector = SelectKeyFeature(data_file="./clouddata/tpc-ds.csv")
    # Build feature select model based on training data and linear regression model.
    feature_selector._build_model()
    # Select training points through feature select model.
    selected_points = feature_selector._transform_feature(feature_selector.training_data)
    # print selected_points

    # Initialize ConfigurationSelector with selected training points.
    selector = ConfigurationSelector(training_data_in=selected_points)
    # Build basic performance model.
    basic_model = selector.build()
    # Predict for [cores, machine, fraction, 0]

    # test =  feature_selector._transform_feature([[32, 16, 1, 0]])
    # print test
    print basic_model[0]
    # print selector._get_features(test[0])
    # print selector._get_features(test[0]).dot(basic_model[0])

    test = [feature_selector._transform_feature([[2 * i, i, 1, 0]]) for i in xrange(1, 41, 1)]
    j = 1
    for point in test:
        # print selector._get_features(point[0])
        # print point[0][5]
        print(j)
        j = j + 1
        print selector._get_features(point[0]).dot(basic_model[0])
        print selector._get_features(point[0])
        print basic_model[0]
    # print selector._get_features(test[0][0]).dot(basic_model[0])
    # print selector._get_features(test[1][0]).dot(basic_model[0])
    # print selector._get_features(test[3][0]).dot(basic_model[0])
    # # print selector._get_features(test[4][0]).dot(basic_model[0])
    # print selector._get_features(test[7][0]).dot(basic_model[0])
    # print selector._get_features(test[15][0]).dot(basic_model[0])
    # print selector._get_features(test[31][0]).dot(basic_model[0])
