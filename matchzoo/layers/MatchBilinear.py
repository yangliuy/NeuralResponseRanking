from __future__ import absolute_import

import numpy as np

import keras
from keras import backend as K
from keras.engine import Layer
from keras.engine import InputSpec

# import sys
# sys.path.append('../utils/')
# from utility import *

class MatchBilinear(Layer):
    """Layer that computes a bilinear matching matrix between samples in two tensors.
    """

    def __init__(self, **kwargs):
        super(MatchBilinear, self).__init__(**kwargs)

    def build(self,input_shape):
        # Used purely for shape validation.
        if not isinstance(input_shape, list) or len(input_shape) != 2:
            raise ValueError('A `Match` layer should be called '
                             'on a list of 2 inputs.')
        self.shape1 = input_shape[0]
        self.shape2 = input_shape[1]
        if self.shape1[0] != self.shape2[0]:
            raise ValueError(
                'Dimension incompatibility '
                '%s != %s. ' % (self.shape1[0], self.shape2[0]) +
                'Layer shapes: %s, %s' % (self.shape1, self.shape2)) # batch_size should equal
        if self.shape1[2] != self.shape2[2]:
            raise ValueError(
                'Dimension incompatibility '
                '%s != %s. ' % (self.shape1[2], self.shape2[2]) + # embeding_size should equal
                'Layer shapes: %s, %s' % (self.shape1, self.shape2))

        self.A = self.add_weight( name='A',
                               shape=(self.shape1[2], self.shape2[2]),
                                dtype=K.tf.float32,
                                initializer=K.tf.contrib.layers.xavier_initializer(),
                               trainable=True )
        super(MatchBilinear, self).build(input_shape)  # Be sure to call this somewhere!

    def call(self, inputs):
        x0 = inputs[0]
        x1 = inputs[1]
        # Shape of A_matrix should be the same with the output BiGRU 2m x 2m
        #show_layer_info('A_matrix', self.A)
        x1 = K.tf.transpose(x1, perm=[0, 2, 1])  # transpose response_hidden to  batchSize * hiddenSize * rLen
        #show_layer_info('x[1]_transpose', x1)
        matrix2 = K.tf.einsum('aij,jk->aik', x0, self.A)  # TODO:check this  batchSize * uttLen * hiddenSize
        #show_layer_info('matrix2_after_einsum', matrix2)
        output = K.tf.matmul(matrix2, x1)  # batchSize * uttLen * hiddenSize   batchSize * hiddenSize * rLen -> batchSize * uttLen * rLen
        #show_layer_info('matrix2_after_matmul', matrix2)
        output = K.tf.expand_dims(output, 3)
        return output
        #output = K.tf.einsum('abd,fde,ace->afbc', x1, self.M, x2)
        #return output

    def compute_output_shape(self, input_shape):
        if not isinstance(input_shape, list) or len(input_shape) != 2:
            raise ValueError('A `Match` layer should be called '
                             'on a list of 2 inputs.')
        shape1 = list(input_shape[0])
        shape2 = list(input_shape[1])
        output_shape = [shape1[0], shape1[1], shape2[1], 1]
        return tuple(output_shape)
