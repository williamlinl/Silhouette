import numpy as np
import cvxpy as cvx
import argparse


class DesignOfExperiment(object):

    MIN_WEIGHT_FOR_SELECTION = 0.1

    '''
    A Design of Experiment.
    '''
    def __init__(self, parts_min, parts_max, total_parts,
                 mcs_min=1, mcs_max=16, cores_per_mc=2,
                 budget=10.0, num_parts_interpolate=16):
        '''
        Create an sample design instance.

        :param self: The object being created
        :type self: ExperimentDesign
        :param parts_min: Minimum number of partitions to use in experiments
        :type parts_min: int
        :param parts_max: Maximum number of partitions to use in experiments
        :type parts_max: int
        :param total_parts: Total number of partitions in the dataset
        :type total_parts: int
        :param mcs_min: Minimum number of machines to use in experiments
        :type mcs_min: int
        :param mcs_max: Maximum number of machines to use in experiments
        :type mcs_max: int
        :param cores_per_mc: Cores or slots available per machine
        :type cores_per_mc: int
        :param budget: Budget for the experiment design problem
        :type budget: float
        :param num_parts_interpolate: Number of samples to interpolate between parts_min and parts_max
        :type budget: float
        '''
        self.parts_min = parts_min
        self.parts_max = parts_max
        self.total_parts = total_parts
        self.mcs_min = mcs_min
        self.mcs_max = mcs_max
        self.cores_per_mc = cores_per_mc
        self.budget = budget
        self.num_parts_interpolate = num_parts_interpolate

    def _get_all_samples(self):
        '''Enumerate all the training samples given the params for experiment design'''
        mcs_range = xrange(self.mcs_min, self.mcs_max + 1)

        scale_min = float(self.parts_min) / float(self.total_parts)
        scale_max = float(self.parts_max) / float(self.total_parts)
        scale_range = np.linspace(scale_min, scale_max, self.num_parts_interpolate)

        for scale in scale_range:
            for mcs in mcs_range:
                if np.round(scale * self.total_parts) >= self.cores_per_mc * mcs:
                    yield [scale, mcs]

    def _get_features(self, training_sample):
        ''' Compute the features for a given sample. Sample is expected to be [input_frac, machines]'''
        fraction = training_sample[0]
        machine = training_sample[1]
        cores = machine * self.cores_per_mc
        return [1, float(fraction) / float(cores), np.sqrt(fraction) / float(cores),
                float(machine), np.log(machine), np.square(machine)]

    @staticmethod
    def _get_covariance_matrices(features_arr):
        ''' Returns a list of covariance matrices given expt design features'''
        col_means = np.mean(features_arr, axis=0)
        means_inv = (1.0 / col_means)
        num_row = features_arr.shape[0]
        for i in xrange(0, num_row):
            feature_row = features_arr[i,]
            ftf = np.outer(feature_row.transpose(), feature_row)
            yield np.diag(means_inv).transpose().dot(ftf.dot(np.diag(means_inv)))

    @staticmethod
    def _construct_objective(covariance_matrices, weights):
        ''' Constructs the CVX objective function. '''
        num_samples = len(covariance_matrices)
        num_dim = int(covariance_matrices[0].shape[0])
        matrix_part = np.zeros([num_dim, num_dim])
        for j in xrange(0, num_samples):
            matrix_part = matrix_part + covariance_matrices[j] * weights[j]
        # A-optimality criteria
        # objective = 0
        # for i in xrange(0, num_dim):
        #     k_vec = np.zeros(num_dim)
        #     k_vec[i] = 1.0
        #     objective = objective + cvx.matrix_frac(k_vec, matrix_part)
        # D-optimality criteria
        objective = cvx.log_det(matrix_part)
        return objective

    def _get_cost(self, weights, samples):
        '''Estimate the cost of an experiment. Right now this is input_frac/machines'''
        cost = 0
        num_samples = len(samples)
        scale_min = float(self.parts_min) / float(self.total_parts)
        for i in xrange(0, num_samples):
            scale = samples[i][0]
            mcs = samples[i][1]
            cost = cost + (float(scale) / scale_min * 1.0 / float(mcs) * weights[i])
        return cost

    def _construct_constraints(self, weights, samples):
        '''Construct non-negative weights and budget constraints'''
        constraints = []
        constraints.append(0 <= weights)
        constraints.append(weights <= 1)
        constraints.append(self._get_cost(weights, samples) <= self.budget)
        return constraints

    def _convert_to_parts(self, fraction):
        '''Convert input fraction into number of partitions'''
        return int(np.ceil(fraction * self.total_parts))

    def run(self):
        ''' Run experiment design. Returns a list of configurations and their scores'''
        all_samples = list(self._get_all_samples())
        num_samples = len(all_samples)

        all_sample_features = np.array([self._get_features(sample) for sample in all_samples])
        covariance_matrices = list(self._get_covariance_matrices(all_sample_features))

        weights = cvx.Variable(num_samples)
        objective = cvx.Maximize(self._construct_objective(covariance_matrices, weights))
        constraints = self._construct_constraints(weights, all_samples)
        problem = cvx.Problem(objective, constraints)
        opt_val = problem.solve()
        # TODO: Add debug logging
        # print "solution status ", problem.status
        # print "opt value is ", opt_val

        filtered_weights_idxs = []
        for i in range(0, num_samples):
            if weights[i].value > self.MIN_WEIGHT_FOR_SELECTION:
                filtered_weights_idxs.append((weights[i].value, i))

        sorted_by_weight = sorted(filtered_weights_idxs, key=lambda t: t[0], reverse=True)
        return [(self._convert_to_parts(all_samples[idx][0]), all_samples[idx][0],
                 all_samples[idx][1], l) for (l, idx) in sorted_by_weight]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Design of Experiment')

    parser.add_argument('--min-parts', type=int, required=True,
        help='Minimum number of partitions to use in experiments')
    parser.add_argument('--max-parts', type=int, required=True,
        help='Maximum number of partitions to use in experiments')
    parser.add_argument('--total-parts', type=int, required=True,
        help='Total number of partitions in the dataset')
    parser.add_argument('--min-mcs', type=int, required=True,
        help='Minimum number of machines to use in experiments')
    parser.add_argument('--max-mcs', type=int, required=True,
        help='Maximum number of machines to use in experiments')
    parser.add_argument('--cores-per-mc', type=int, default=2,
        help='Number of cores or slots available per machine, (default 2)')
    parser.add_argument('--budget', type=float, default=5.0,
        help='Budget of experiment design problem, (default 10.0)')
    parser.add_argument('--num-parts-interpolate', type=int, default=20,
        help='Number of samples to interpolate between min_parts and max_parts, (default 16)')

    args = parser.parse_args()

    ex = DesignOfExperiment(args.min_parts, args.max_parts, args.total_parts,
        args.min_mcs, args.max_mcs, args.cores_per_mc, args.budget,
        args.num_parts_interpolate)

    expts = ex.run()
    print "Machines, InputFraction, Weight"
    # for expt in expts:
    #     print "%d" % (expt[2])
    # for expt in expts:
    #     print "%f" % (expt[1])
    # for expt in expts:
    #     print "%f" % (expt[3])
    for expt in expts:
        print "%d, %f, %f" % (expt[2], expt[1], expt[3])
